INSERT INTO script_types (id, type) VALUES (6, 'SLP_GENESIS_SCRIPT'), (7, 'SLP_SEND_SCRIPT'), (8, 'SLP_MINT_SCRIPT'), (9, 'SLP_COMMIT_SCRIPT');

ALTER TABLE locking_scripts
    ADD COLUMN slp_transaction_id INT UNSIGNED,
    ADD FOREIGN KEY locking_scripts_slp_tx_id_fk (slp_transaction_id) REFERENCES transactions (id)
;

CREATE TABLE validated_slp_transactions (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    transaction_id INT UNSIGNED NOT NULL,
    blockchain_segment_id INT UNSIGNED NOT NULL,
    is_valid TINYINT(1) UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY valid_slp_transactions_uq (transaction_id, blockchain_segment_id),
    FOREIGN KEY valid_slp_transactions_tx_id_fk (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE,
    FOREIGN KEY valid_slp_transactions_blockchain_segment_id_fk (blockchain_segment_id) REFERENCES blockchain_segments (id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

INSERT INTO metadata (version, timestamp) VALUES (2, UNIX_TIMESTAMP());

INSERT INTO address_processor_queue (locking_script_id) SELECT id FROM locking_scripts;
