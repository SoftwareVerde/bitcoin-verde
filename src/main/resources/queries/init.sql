CREATE TABLE metadata (
    id int unsigned NOT NULL AUTO_INCREMENT,
    version int unsigned NOT NULL DEFAULT '1',
    timestamp bigint unsigned NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY metadata_version_uq (version)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8;

CREATE TABLE blocks (
    id int unsigned NOT NULL AUTO_INCREMENT,
    hash varchar(64) NOT NULL,
    previous_block_id int unsigned,
    merkle_root varchar(64) NOT NULL,
    version int NOT NULL DEFAULT '1',
    timestamp bigint unsigned NOT NULL,
    difficulty varchar(8) NOT NULL,
    nonce bigint unsigned NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY block_hash_uq (hash),
    FOREIGN KEY block_previous_block_id_ix (previous_block_id) REFERENCES blocks (id),
    INDEX block_timestamp_ix (timestamp) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=UTF8;

CREATE TABLE transactions (
    id int unsigned NOT NULL AUTO_INCREMENT,
    hash varchar(64) NOT NULL,
    block_id int unsigned,
    version int NOT NULL,
    has_witness_data tinyint(1) NOT NULL,
    lock_time bigint unsigned NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY transaction_hash_uq (hash),
    FOREIGN KEY transaction_block_id_ix (block_id) REFERENCES blocks (id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8;

CREATE TABLE transaction_outputs (
    id int unsigned NOT NULL AUTO_INCREMENT,
    transaction_id int unsigned NOT NULL,
    `index` int unsigned NOT NULL,
    amount bigint unsigned NOT NULL,
    script blob NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY transaction_output_tx_id_index_uq (transaction_id, `index`),
    FOREIGN KEY transaction_outputs_tx_id_ix (transaction_id) REFERENCES transactions (id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8;

CREATE TABLE transaction_inputs (
    id int unsigned NOT NULL AUTO_INCREMENT,
    transaction_id int unsigned NOT NULL,
    previous_transaction_output_id int unsigned,
    signature_script blob NOT NULL,
    sequence_number int unsigned NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY transaction_inputs_tx_id_prev_tx_id_uq (transaction_id, previous_transaction_output_id),
    FOREIGN KEY transaction_inputs_tx_id_ix (transaction_id) REFERENCES transactions (id),
    FOREIGN KEY transaction_inputs_tx_out_ix (previous_transaction_output_id) REFERENCES transaction_outputs (id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8;

INSERT INTO metadata (version, timestamp) VALUES (1, UNIX_TIMESTAMP());
