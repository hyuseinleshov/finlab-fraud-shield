-- V3__seed_vendors.sql

INSERT INTO vendors (vendor_id, name, iban, risk_level, total_transactions, flagged_transactions, is_active)
SELECT * FROM (VALUES
    (1, 'Technopolis Sofia', (SELECT iban FROM ibans WHERE iban LIKE 'BG__BNBG%' AND is_risky = FALSE LIMIT 1), 'LOW', 1247, 3, TRUE),
    (2, 'Metro Cash & Carry Bulgaria', (SELECT iban FROM ibans WHERE iban LIKE 'BG__FINV%' AND is_risky = FALSE LIMIT 1 OFFSET 1), 'LOW', 3421, 5, TRUE),
    (3, 'Kaufland Bulgaria', (SELECT iban FROM ibans WHERE iban LIKE 'BG__UNCR%' AND is_risky = FALSE LIMIT 1), 'LOW', 5892, 8, TRUE),
    (4, 'Billa Bulgaria', (SELECT iban FROM ibans WHERE iban LIKE 'BG__STSA%' AND is_risky = FALSE LIMIT 1), 'LOW', 4521, 6, TRUE),
    (5, 'OMV Bulgaria', (SELECT iban FROM ibans WHERE iban LIKE 'BG__RZBB%' AND is_risky = FALSE LIMIT 1), 'LOW', 2134, 2, TRUE),
    (10, 'Sofia Computer Systems', (SELECT iban FROM ibans WHERE iban LIKE 'BG__CECB%' AND is_risky = FALSE LIMIT 1), 'MEDIUM', 342, 15, TRUE),
    (11, 'Balkan Trading Ltd', (SELECT iban FROM ibans WHERE iban LIKE 'BG__IORT%' AND is_risky = FALSE LIMIT 1), 'MEDIUM', 567, 28, TRUE),
    (12, 'Varna Import Export', (SELECT iban FROM ibans WHERE iban LIKE 'BG__BUIN%' AND is_risky = FALSE LIMIT 1), 'MEDIUM', 234, 12, TRUE),
    (13, 'Plovdiv Electronics', (SELECT iban FROM ibans WHERE iban LIKE 'BG__CREX%' AND is_risky = FALSE LIMIT 1), 'MEDIUM', 189, 11, TRUE),
    (14, 'Burgas Wholesale', (SELECT iban FROM ibans WHERE iban LIKE 'BG__FINV%' AND is_risky = FALSE LIMIT 1 OFFSET 2), 'MEDIUM', 445, 22, TRUE),
    (20, 'Quick Cash Services', (SELECT iban FROM ibans WHERE iban LIKE 'BG__BANK%' AND is_risky = TRUE LIMIT 1), 'HIGH', 45, 18, TRUE),
    (21, 'Express Trading BG', (SELECT iban FROM ibans WHERE iban LIKE 'BG__UNCR%' AND is_risky = TRUE LIMIT 1 OFFSET 1), 'HIGH', 67, 25, TRUE),
    (22, 'Fast Logistics Ltd', (SELECT iban FROM ibans WHERE iban LIKE 'BG__STSA%' AND is_risky = TRUE LIMIT 1 OFFSET 1), 'HIGH', 34, 15, TRUE),
    (999, 'Closed Business Ltd', (SELECT iban FROM ibans WHERE iban LIKE 'BG__BNBG%' AND is_risky = FALSE LIMIT 1 OFFSET 1), 'HIGH', 12, 8, FALSE)
) AS v(vendor_id, name, iban, risk_level, total_transactions, flagged_transactions, is_active);

-- Simulate recent activity for realistic demo
UPDATE vendors
SET last_transaction_at = CURRENT_TIMESTAMP - (random() * INTERVAL '30 days')
WHERE is_active = TRUE;
DO $$
DECLARE
    invalid_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO invalid_count
    FROM vendors v
    LEFT JOIN ibans i ON v.iban = i.iban
    WHERE i.iban IS NULL;

    IF invalid_count > 0 THEN
        RAISE EXCEPTION 'Found % vendors with invalid IBANs', invalid_count;
    END IF;

    RAISE NOTICE '[VENDORS] ✓ All vendor IBANs validated against ibans table';
END $$;
