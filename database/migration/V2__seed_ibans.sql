-- UNLOGGED skips WAL for faster bulk insert (acceptable for seed data)
CREATE UNLOGGED TABLE ibans_temp (
    iban VARCHAR(22),
    is_risky BOOLEAN
);

CREATE OR REPLACE FUNCTION calculate_bg_iban_check(
    bank_code VARCHAR(4),
    branch_code VARCHAR(4),
    account_type VARCHAR(2),
    account_number VARCHAR(8)
) RETURNS VARCHAR(2) AS $$
DECLARE
    iban_base TEXT;
    numeric_str TEXT;
    remainder INTEGER;
    check_digits INTEGER;
    c CHAR(1);
BEGIN
    iban_base := bank_code || branch_code || account_type || account_number || 'BG00';

    -- Character-by-character conversion needed (TRANSLATE has bug with multi-digit replacements)
    numeric_str := '';
    FOR i IN 1..LENGTH(iban_base) LOOP
        c := SUBSTRING(iban_base FROM i FOR 1);
        IF c BETWEEN 'A' AND 'Z' THEN
            numeric_str := numeric_str || (ASCII(c) - ASCII('A') + 10)::TEXT;
        ELSE
            numeric_str := numeric_str || c;
        END IF;
    END LOOP;

    -- Piece-wise prevents NUMERIC overflow on long strings
    remainder := 0;
    FOR i IN 1..LENGTH(numeric_str) BY 7 LOOP
        remainder := ((remainder::TEXT || SUBSTRING(numeric_str FROM i FOR 7))::BIGINT % 97)::INTEGER;
    END LOOP;

    check_digits := 98 - remainder;
    RETURN LPAD(check_digits::TEXT, 2, '0');
END;
$$ LANGUAGE plpgsql IMMUTABLE;

DO $$
DECLARE
    batch_size INTEGER := 50000;
    total_batches INTEGER := 20;
    current_batch INTEGER;
    bank_code VARCHAR(4);
    branch_code VARCHAR(4);
    account_number VARCHAR(10);
    check_digits VARCHAR(2);
    full_iban VARCHAR(22);
    is_risky_flag BOOLEAN;
    batch_start INTEGER;
    batch_end INTEGER;
BEGIN
    FOR current_batch IN 1..total_batches LOOP
        batch_start := (current_batch - 1) * batch_size + 1;
        batch_end := current_batch * batch_size;

        INSERT INTO ibans_temp (iban, is_risky)
        SELECT
            'BG' ||
            calculate_bg_iban_check(
                CASE (gs % 10)
                    WHEN 0 THEN 'BANK' WHEN 1 THEN 'UNCR' WHEN 2 THEN 'STSA'
                    WHEN 3 THEN 'RZBB' WHEN 4 THEN 'BNBG' WHEN 5 THEN 'FINV'
                    WHEN 6 THEN 'IORT' WHEN 7 THEN 'BUIN' WHEN 8 THEN 'CECB'
                    ELSE 'CREX'
                END,
                LPAD(((gs / 100) % 10000)::TEXT, 4, '0'),
                LPAD(((gs / 10) % 100)::TEXT, 2, '0'),
                LPAD((gs % 100000000)::TEXT, 8, '0')
            ) ||
            CASE (gs % 10)
                WHEN 0 THEN 'BANK' WHEN 1 THEN 'UNCR' WHEN 2 THEN 'STSA'
                WHEN 3 THEN 'RZBB' WHEN 4 THEN 'BNBG' WHEN 5 THEN 'FINV'
                WHEN 6 THEN 'IORT' WHEN 7 THEN 'BUIN' WHEN 8 THEN 'CECB'
                ELSE 'CREX'
            END ||
            LPAD(((gs / 100) % 10000)::TEXT, 4, '0') ||
            LPAD(((gs / 10) % 100)::TEXT, 2, '0') ||
            LPAD((gs % 100000000)::TEXT, 8, '0') AS iban,
            (gs % 100) < 10 AS is_risky
        FROM generate_series(batch_start, batch_end) AS gs;

        RAISE NOTICE '[IBAN] Progress: % / 1,000,000 (% complete)',
            batch_end, ROUND((batch_end::DECIMAL / 1000000 * 100)::NUMERIC, 0)::TEXT || '%';
    END LOOP;

    RAISE NOTICE '[IBAN] ✓ Generated 1,000,000 VALID Bulgarian IBANs';
END $$;

CREATE TABLE ibans (
    id BIGSERIAL PRIMARY KEY,
    iban VARCHAR(22) NOT NULL,
    is_risky BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (LENGTH(iban) = 22)
);

INSERT INTO ibans (iban, is_risky)
SELECT iban, is_risky FROM ibans_temp;

-- Create indexes after bulk insert for better performance
CREATE UNIQUE INDEX idx_ibans_iban_unique ON ibans(iban);
CREATE INDEX idx_ibans_risky ON ibans(iban) WHERE is_risky = TRUE;

DROP TABLE ibans_temp;
DROP FUNCTION calculate_bg_iban_check(VARCHAR, VARCHAR, VARCHAR, VARCHAR);
DO $$
DECLARE
    total_count INTEGER;
    risky_count INTEGER;
    sample_iban VARCHAR(22);
    sample_base TEXT;
    numeric_str TEXT;
    calculated_mod INTEGER;
    c CHAR(1);
BEGIN
    SELECT COUNT(*) INTO total_count FROM ibans;
    SELECT COUNT(*) INTO risky_count FROM ibans WHERE is_risky = TRUE;

    IF total_count != 1000000 THEN
        RAISE EXCEPTION 'Expected 1,000,000 IBANs but generated %', total_count;
    END IF;

    SELECT iban INTO sample_iban FROM ibans ORDER BY random() LIMIT 1;
    sample_base := SUBSTRING(sample_iban FROM 5) || SUBSTRING(sample_iban FROM 1 FOR 4);

    numeric_str := '';
    FOR i IN 1..LENGTH(sample_base) LOOP
        c := SUBSTRING(sample_base FROM i FOR 1);
        IF c BETWEEN 'A' AND 'Z' THEN
            numeric_str := numeric_str || (ASCII(c) - ASCII('A') + 10)::TEXT;
        ELSE
            numeric_str := numeric_str || c;
        END IF;
    END LOOP;

    calculated_mod := (numeric_str::NUMERIC % 97)::INTEGER;

    IF calculated_mod != 1 THEN
        RAISE WARNING 'Sample IBAN % failed MOD 97 validation (remainder: %)', sample_iban, calculated_mod;
    ELSE
        RAISE NOTICE '[IBAN] ✓ MOD 97 validation passed for sample: %', sample_iban;
    END IF;

    RAISE NOTICE '[IBAN] Summary: % total, % risky (%)',
        total_count, risky_count, ROUND((risky_count::DECIMAL / total_count * 100)::NUMERIC, 1)::TEXT || '%';
END $$;
