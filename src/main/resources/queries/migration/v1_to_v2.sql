INSERT INTO script_types (id, type) VALUES (6, 'SLP_GENESIS_SCRIPT'), (7, 'SLP_SEND_SCRIPT'), (8, 'SLP_MINT_SCRIPT'), (9, 'SLP_COMMIT_SCRIPT');

ALTER TABLE locking_scripts
    ADD COLUMN slp_transaction_id INT UNSIGNED,
    ADD FOREIGN KEY locking_scripts_slp_tx_id_fk (slp_transaction_id) REFERENCES transactions (id)
;

INSERT INTO metadata (version, timestamp) VALUES (2, UNIX_TIMESTAMP());