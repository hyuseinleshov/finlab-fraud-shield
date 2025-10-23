CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    full_name VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_locked BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (LENGTH(username) >= 3),
    CHECK (LENGTH(email) >= 5)
);

CREATE INDEX idx_users_username ON users(username) WHERE is_active = TRUE;
CREATE INDEX idx_users_email ON users(email) WHERE is_active = TRUE;

-- Dual storage (Redis + DB) enables instant revocation with persistence
CREATE TABLE jwt_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token TEXT NOT NULL,
    token_type VARCHAR(20) NOT NULL DEFAULT 'ACCESS',
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT unique_token UNIQUE (token),
    CONSTRAINT fk_jwt_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_jwt_user_id ON jwt_tokens(user_id) WHERE is_revoked = FALSE;
CREATE INDEX idx_jwt_expires_at ON jwt_tokens(expires_at) WHERE is_revoked = FALSE;
CREATE INDEX idx_jwt_active_tokens ON jwt_tokens(token) WHERE is_revoked = FALSE;

-- Immutable for compliance (7-year retention required)
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

-- BRIN for time-series efficiency on large datasets
CREATE INDEX idx_audit_timestamp ON audit_log USING BRIN (timestamp) WITH (pages_per_range = 128);
CREATE INDEX idx_audit_details ON audit_log USING GIN (details);
CREATE INDEX idx_audit_user_action ON audit_log(user_id, action, timestamp DESC);

CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(100) NOT NULL UNIQUE,
    iban VARCHAR(22) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'BGN',
    vendor_id BIGINT,
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

CREATE INDEX idx_transactions_iban_date ON transactions(iban, created_at DESC);
CREATE INDEX idx_transactions_review_queue ON transactions(created_at DESC) WHERE decision = 'REVIEW';
CREATE INDEX idx_transactions_vendor ON transactions(vendor_id, created_at DESC);
-- Partial index for high-risk transactions only (optimization)
CREATE INDEX idx_transactions_fraud_score ON transactions(fraud_score DESC, created_at DESC) WHERE fraud_score > 70;
CREATE INDEX idx_transactions_timeseries ON transactions USING BRIN (created_at) WITH (pages_per_range = 128);
CREATE INDEX idx_transactions_risk_factors ON transactions USING GIN (risk_factors);

CREATE TABLE vendors (
    id BIGSERIAL PRIMARY KEY,
    vendor_id VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    iban VARCHAR(22) NOT NULL,
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
    CHECK (fraud_rate >= 0.00 AND fraud_rate <= 100.00),
    CHECK (LENGTH(iban) = 22)
);

CREATE INDEX idx_vendors_vendor_id ON vendors(vendor_id) WHERE is_active = TRUE;
CREATE INDEX idx_vendors_iban ON vendors(iban) WHERE is_active = TRUE;
CREATE INDEX idx_vendors_risk_level ON vendors(risk_level, fraud_rate DESC) WHERE is_active = TRUE AND risk_level IN ('HIGH', 'MEDIUM');

-- Preserve transactions if vendor deleted
ALTER TABLE transactions
ADD CONSTRAINT fk_transactions_vendor
FOREIGN KEY (vendor_id) REFERENCES vendors(id)
ON DELETE SET NULL;
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Auto-calculate fraud rate from flagged/total ratio
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

CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_transactions_updated_at
    BEFORE UPDATE ON transactions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_vendors_updated_at
    BEFORE UPDATE ON vendors
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER calculate_vendor_fraud_rate
    BEFORE INSERT OR UPDATE ON vendors
    FOR EACH ROW
    EXECUTE FUNCTION update_vendor_fraud_rate();
