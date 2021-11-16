TRUNCATE TABLE committed_unspent_transaction_outputs;
UPDATE properties SET `value` = 0 WHERE `key` = 'committed_utxo_block_height';
ALTER TABLE committed_unspent_transaction_outputs DROP COLUMN is_spent, ADD COLUMN amount BIGINT NOT NULL AFTER block_height, ADD COLUMN locking_script BLOB NOT NULL;

CREATE TABLE pruned_previous_transaction_outputs (
    transaction_hash BINARY(32) NOT NULL,
    `index` INT UNSIGNED NOT NULL,
    block_height INT UNSIGNED NOT NULL,
    amount BIGINT NOT NULL,
    locking_script BLOB NOT NULL,
    expires_after_block_height INT UNSIGNED NOT NULL,
    PRIMARY KEY (transaction_hash, `index`),
    INDEX pruned_prevouts_block_height (expires_after_block_height) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

CREATE TABLE pruned_previous_transaction_outputs_buffer LIKE pruned_previous_transaction_outputs;

DELIMITER //
CREATE PROCEDURE ROTATE_PRUNED_PREVIOUS_TRANSACTION_OUTPUTS()
BEGIN
    RENAME TABLE pruned_previous_transaction_outputs TO pruned_previous_transaction_outputs_tmp, pruned_previous_transaction_outputs_buffer TO pruned_previous_transaction_outputs, pruned_previous_transaction_outputs_tmp TO pruned_previous_transaction_outputs_buffer;
    TRUNCATE TABLE pruned_previous_transaction_outputs_buffer;
END //
DELIMITER ;