-- V2__seed_ibans.sql
-- Generate 1 million valid Bulgarian IBANs (Assignment requirement)
CREATE TABLE ibans (
    id BIGSERIAL PRIMARY KEY,
    iban VARCHAR(22) NOT NULL UNIQUE,
    is_risky BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (LENGTH(iban) = 22)
);

CREATE INDEX idx_ibans_lookup ON ibans(iban);
CREATE INDEX idx_ibans_risky ON ibans(iban) WHERE is_risky = TRUE;

-- ISO 7064 MOD 97 IBAN validation functions
CREATE OR REPLACE FUNCTION letter_to_number(letter CHAR) RETURNS TEXT AS $$
BEGIN
    RETURN (ASCII(UPPER(letter)) - ASCII('A') + 10)::TEXT;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION string_to_iban_numeric(input_str TEXT) RETURNS TEXT AS $$
DECLARE
    result TEXT := '';
    i INTEGER;
    current_char CHAR;
BEGIN
    FOR i IN 1..LENGTH(input_str) LOOP
        current_char := SUBSTRING(input_str FROM i FOR 1);
        IF current_char BETWEEN 'A' AND 'Z' OR current_char BETWEEN 'a' AND 'z' THEN
            result := result || letter_to_number(current_char);
        ELSE
            result := result || current_char;
        END IF;
    END LOOP;
    RETURN result;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION calculate_iban_check_digits(
    bank_code VARCHAR(4),
    branch_code VARCHAR(4),
    account_number VARCHAR(10)
) RETURNS VARCHAR(2) AS $$
DECLARE
    iban_base TEXT;
    iban_numeric TEXT;
    check_digits INTEGER;
BEGIN
    iban_base := bank_code || branch_code || account_number || 'BG00';
    iban_numeric := string_to_iban_numeric(iban_base);
    check_digits := 98 - (iban_numeric::NUMERIC % 97);
    RETURN LPAD(check_digits::TEXT, 2, '0');
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Generate 1M IBANs with 10% marked as risky
CREATE OR REPLACE FUNCTION generate_bulgarian_ibans(total_count INTEGER, risky_percentage DECIMAL)
RETURNS VOID AS $$
DECLARE
    bank_code VARCHAR(4);
    branch_code VARCHAR(4);
    account_number VARCHAR(10);
    check_digits VARCHAR(2);
    full_iban VARCHAR(22);
    is_risky_flag BOOLEAN;
    batch_size INTEGER := 10000;
    current_batch INTEGER := 0;
    i INTEGER;
BEGIN
    FOR i IN 1..total_count LOOP
        bank_code := CASE (i % 10)
            WHEN 0 THEN 'BANK' WHEN 1 THEN 'UNCR' WHEN 2 THEN 'STSA'
            WHEN 3 THEN 'RZBB' WHEN 4 THEN 'BNBG' WHEN 5 THEN 'FINV'
            WHEN 6 THEN 'IORT' WHEN 7 THEN 'BUIN' WHEN 8 THEN 'CECB'
            ELSE 'CREX'
        END;

        branch_code := LPAD(((i / 100) % 10000)::TEXT, 4, '0');
        account_number := LPAD((i % 10000000000)::TEXT, 10, '0');
        check_digits := calculate_iban_check_digits(bank_code, branch_code, account_number);
        full_iban := 'BG' || check_digits || bank_code || branch_code || account_number;
        is_risky_flag := (i % 100) < (risky_percentage * 100);

        INSERT INTO ibans (iban, is_risky) VALUES (full_iban, is_risky_flag);

        IF i % batch_size = 0 THEN
            current_batch := current_batch + 1;
            RAISE NOTICE 'Generated % IBANs (% batches)', i, current_batch;
        END IF;
    END LOOP;

    RAISE NOTICE 'Successfully generated % Bulgarian IBANs', total_count;
END;
$$ LANGUAGE plpgsql;

SELECT generate_bulgarian_ibans(1000000, 0.10);

DROP FUNCTION generate_bulgarian_ibans(INTEGER, DECIMAL);
DROP FUNCTION string_to_iban_numeric(TEXT);
DROP FUNCTION letter_to_number(CHAR);

-- Verify results
DO $$
DECLARE
    total_count INTEGER;
    risky_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO total_count FROM ibans;
    SELECT COUNT(*) INTO risky_count FROM ibans WHERE is_risky = TRUE;

    IF total_count != 1000000 THEN
        RAISE EXCEPTION 'Expected 1,000,000 IBANs but generated %', total_count;
    END IF;

    RAISE NOTICE 'IBAN Generation: % total, % risky (%.2f%%)',
        total_count, risky_count, (risky_count::DECIMAL / total_count * 100);
END $$;
