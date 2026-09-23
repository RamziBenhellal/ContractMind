-- Existing databases were created by Hibernate (ddl-auto: update).
-- This script is additive and safe to re-run against both empty and existing schemas.

CREATE TABLE IF NOT EXISTS bank_calendar_entries (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT         NOT NULL,
    title               VARCHAR(255)   NOT NULL,
    expected_date       DATE           NOT NULL,
    expected_amount     NUMERIC(12, 2) NOT NULL,
    type                VARCHAR(32)    NOT NULL,
    recurrence_rule     VARCHAR(255),
    source_contract_id  BIGINT,
    source_income_id    BIGINT
);

CREATE INDEX IF NOT EXISTS idx_calendar_entries_user_id
    ON bank_calendar_entries (user_id);
CREATE INDEX IF NOT EXISTS idx_calendar_entries_expected_date
    ON bank_calendar_entries (user_id, expected_date);

CREATE TABLE IF NOT EXISTS classification_rules (
    id                           BIGSERIAL PRIMARY KEY,
    user_id                      BIGINT       NOT NULL,
    match_type                   VARCHAR(32)  NOT NULL,
    match_value                  VARCHAR(255) NOT NULL,
    target_type                  VARCHAR(32)  NOT NULL,
    target_entity_id             BIGINT       NOT NULL,
    learned_from_transaction_id  BIGINT,
    created_at                   TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_classification_rules_user_id
    ON classification_rules (user_id);
CREATE INDEX IF NOT EXISTS idx_classification_rules_match_type
    ON classification_rules (user_id, match_type);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = current_schema() AND table_name = 'bank_accounts'
    ) THEN
        ALTER TABLE bank_accounts ADD COLUMN IF NOT EXISTS balance NUMERIC(12, 2);
        ALTER TABLE bank_accounts ADD COLUMN IF NOT EXISTS last_synced_at TIMESTAMP;

        UPDATE bank_accounts
        SET account_type = CASE
            WHEN account_type IS NULL THEN 'GIROKONTO'
            WHEN UPPER(REPLACE(REPLACE(account_type, ' ', ''), '-', '')) IN
                 ('GIROKONTO', 'KREDITKARTE', 'PAYPAL', 'KRYPTO')
                THEN UPPER(REPLACE(REPLACE(account_type, ' ', ''), '-', ''))
            WHEN UPPER(account_type) LIKE '%KREDIT%'
              OR UPPER(account_type) LIKE '%CREDIT%'
              OR UPPER(account_type) LIKE '%CARD%' THEN 'KREDITKARTE'
            WHEN UPPER(account_type) LIKE '%PAYPAL%' THEN 'PAYPAL'
            WHEN UPPER(account_type) LIKE '%KRYPTO%'
              OR UPPER(account_type) LIKE '%CRYPTO%' THEN 'KRYPTO'
            ELSE 'GIROKONTO'
        END
        WHERE account_type IS NULL
           OR account_type NOT IN ('GIROKONTO', 'KREDITKARTE', 'PAYPAL', 'KRYPTO');
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = current_schema() AND table_name = 'bank_transactions'
    ) THEN
        ALTER TABLE bank_transactions ADD COLUMN IF NOT EXISTS currency VARCHAR(3);
        ALTER TABLE bank_transactions ADD COLUMN IF NOT EXISTS value_date DATE;
        ALTER TABLE bank_transactions ADD COLUMN IF NOT EXISTS classification VARCHAR(32);
        ALTER TABLE bank_transactions ADD COLUMN IF NOT EXISTS confidence_score DOUBLE PRECISION;
        ALTER TABLE bank_transactions ADD COLUMN IF NOT EXISTS linked_contract_id BIGINT;
        ALTER TABLE bank_transactions ADD COLUMN IF NOT EXISTS linked_income_id BIGINT;
        ALTER TABLE bank_transactions ADD COLUMN IF NOT EXISTS counterparty_name VARCHAR(255);
        ALTER TABLE bank_transactions ADD COLUMN IF NOT EXISTS counterparty_iban VARCHAR(255);

        UPDATE bank_transactions SET currency = 'EUR' WHERE currency IS NULL;
        UPDATE bank_transactions SET classification = 'UNCLASSIFIED' WHERE classification IS NULL;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'bank_transactions'
              AND column_name = 'counterpart_name'
        ) THEN
            UPDATE bank_transactions
            SET counterparty_name = counterpart_name
            WHERE counterparty_name IS NULL AND counterpart_name IS NOT NULL;
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'bank_transactions'
              AND column_name = 'counterpart_iban'
        ) THEN
            UPDATE bank_transactions
            SET counterparty_iban = counterpart_iban
            WHERE counterparty_iban IS NULL AND counterpart_iban IS NOT NULL;
        END IF;
    END IF;
END $$;
