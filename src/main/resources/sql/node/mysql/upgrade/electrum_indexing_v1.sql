ALTER TABLE indexed_transaction_outputs
    DROP COLUMN address,
    ADD COLUMN address BINARY(20) AFTER AMOUNT,
    ADD COLUMN script_hash BINARY(32),
    ADD INDEX indexed_transaction_outputs_script_hash (script_hash) USING BTREE
;

DELETE FROM properties WHERE `key` = 'last_indexed_transaction_id';