ALTER TABLE indexed_transaction_outputs ADD COLUMN memo_action_type BINARY(2);
INSERT INTO script_types (id, type) VALUES (10, 'MEMO_SCRIPT');
UPDATE properties SET value = 0 WHERE `key` = 'last_indexed_transaction_id';