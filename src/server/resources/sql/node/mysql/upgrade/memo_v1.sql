ALTER TABLE indexed_transaction_outputs
    ADD COLUMN memo_action_type BINARY(2),
    ADD COLUMN memo_action_identifier VARBINARY(255),
    ADD INDEX indexed_transaction_outputs_memo_identifier (memo_action_identifier) USING BTREE
;
INSERT INTO script_types (id, type) VALUES (10, 'MEMO_SCRIPT');
UPDATE properties SET value = 0 WHERE `key` = 'last_indexed_transaction_id';
