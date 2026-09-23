ALTER TABLE bank_transactions DROP CONSTRAINT IF EXISTS bank_transactions_classification_status_check;
ALTER TABLE bank_transactions ADD CONSTRAINT bank_transactions_classification_status_check
    CHECK (classification_status IN ('PENDING', 'PENDING_REVIEW', 'CLASSIFIED', 'SKIPPED'));
