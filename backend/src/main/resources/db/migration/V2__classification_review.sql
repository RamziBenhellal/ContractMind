ALTER TABLE bank_transactions ADD COLUMN IF NOT EXISTS suggest_new_contract BOOLEAN;
UPDATE bank_transactions SET suggest_new_contract = FALSE WHERE suggest_new_contract IS NULL;
