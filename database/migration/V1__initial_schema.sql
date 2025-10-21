-- V1__initial_schema.sql
-- Initial schema for FinLab Fraud Shield

-- JWT Tokens Table
-- Stateful JWT storage in database (Assignment requirement: store in BOTH Redis AND database)
CREATE TABLE jwt_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    token TEXT NOT NULL,
    token_type VARCHAR(20) NOT NULL DEFAULT 'ACCESS',
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT unique_token UNIQUE (token)
);

CREATE INDEX idx_jwt_user_id ON jwt_tokens(user_id) WHERE is_revoked = FALSE;
CREATE INDEX idx_jwt_expires_at ON jwt_tokens(expires_at) WHERE is_revoked = FALSE;
CREATE INDEX idx_jwt_active_tokens ON jwt_tokens(token) WHERE is_revoked = FALSE;

-- Audit Log Table
-- Immutable audit trail for authentication and fraud detection events (7-year retention)
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255),
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100),
    resource_id VARCHAR(255),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address INET,
    user_agent TEXT,
    details JSONB,
    CHECK (timestamp <= CURRENT_TIMESTAMP)
);

-- BRIN index for time-series data (100x smaller than B-tree for append-only logs)
CREATE INDEX idx_audit_timestamp ON audit_log USING BRIN (timestamp) WITH (pages_per_range = 128);
CREATE INDEX idx_audit_details ON audit_log USING GIN (details);
CREATE INDEX idx_audit_user_action ON audit_log(user_id, action, timestamp DESC);

-- Transactions Table
-- Invoice payment transactions with fraud detection results
CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(100) NOT NULL UNIQUE,
    iban VARCHAR(22) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'BGN',
    merchant_id VARCHAR(100),
    invoice_number VARCHAR(100),
    fraud_score INTEGER NOT NULL DEFAULT 0,
    decision VARCHAR(20) NOT NULL,
    risk_factors JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    processing_time_ms INTEGER,
    CHECK (fraud_score >= 0 AND fraud_score <= 100),
    CHECK (decision IN ('ALLOW', 'REVIEW', 'BLOCK')),
    CHECK (amount > 0),
    CHECK (LENGTH(iban) = 22)
);

-- Composite index for IBAN + date range queries (fraud pattern analysis)
CREATE INDEX idx_transactions_iban_date ON transactions(iban, created_at DESC);
-- Partial index for review queue (reduces index size)
CREATE INDEX idx_transactions_review_queue ON transactions(created_at DESC) WHERE decision = 'REVIEW';
CREATE INDEX idx_transactions_merchant ON transactions(merchant_id, created_at DESC);
CREATE INDEX idx_transactions_fraud_score ON transactions(fraud_score DESC, created_at DESC) WHERE fraud_score > 70;
-- BRIN index for time-series queries
CREATE INDEX idx_transactions_timeseries ON transactions USING BRIN (created_at) WITH (pages_per_range = 128);
CREATE INDEX idx_transactions_risk_factors ON transactions USING GIN (risk_factors);

-- Vendors Table
-- Merchant/vendor profiles for risk assessment
CREATE TABLE vendors (
    id BIGSERIAL PRIMARY KEY,
    vendor_id VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    iban VARCHAR(22),
    risk_level VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    total_transactions INTEGER NOT NULL DEFAULT 0,
    flagged_transactions INTEGER NOT NULL DEFAULT 0,
    fraud_rate DECIMAL(5, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_transaction_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CHECK (total_transactions >= 0),
    CHECK (flagged_transactions >= 0),
    CHECK (flagged_transactions <= total_transactions),
    CHECK (fraud_rate >= 0.00 AND fraud_rate <= 100.00)
);

CREATE INDEX idx_vendors_vendor_id ON vendors(vendor_id) WHERE is_active = TRUE;
CREATE INDEX idx_vendors_iban ON vendors(iban) WHERE is_active = TRUE;
CREATE INDEX idx_vendors_risk_level ON vendors(risk_level, fraud_rate DESC) WHERE is_active = TRUE AND risk_level IN ('HIGH', 'MEDIUM');

-- Trigger Functions
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_transactions_updated_at
    BEFORE UPDATE ON transactions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_vendors_updated_at
    BEFORE UPDATE ON vendors
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Vendor fraud rate calculation (auto-updates fraud_rate percentage)
CREATE OR REPLACE FUNCTION update_vendor_fraud_rate()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.total_transactions > 0 THEN
        NEW.fraud_rate = (NEW.flagged_transactions::DECIMAL / NEW.total_transactions::DECIMAL) * 100.00;
    ELSE
        NEW.fraud_rate = 0.00;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER calculate_vendor_fraud_rate
    BEFORE INSERT OR UPDATE ON vendors
    FOR EACH ROW
    EXECUTE FUNCTION update_vendor_fraud_rate();
