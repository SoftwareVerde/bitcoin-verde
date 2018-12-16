CREATE TABLE pending_blocks (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    hash CHAR(64) NOT NULL,
    previous_block_hash CHAR(64) NULL,
    timestamp BIGINT UNSIGNED NOT NULL,
    last_download_attempt_timestamp BIGINT UNSIGNED NULL,
    failed_download_count INT UNSIGNED NOT NULL DEFAULT 0,
    priority BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY pending_blocks_uq (hash),
    INDEX pending_blocks_ix1 (priority) USING BTREE,
    INDEX pending_blocks_ix2 (failed_download_count) USING BTREE,
    INDEX pending_blocks_ix3 (previous_block_hash) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE pending_block_data (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    pending_block_id INT UNSIGNED NOT NULL,
    data LONGBLOB NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY pending_block_data_uq (pending_block_id),
    FOREIGN KEY pending_block_data_fk (pending_block_id) REFERENCES pending_blocks (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE pending_transactions (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    hash CHAR(64) NOT NULL,
    timestamp BIGINT UNSIGNED NOT NULL,
    last_download_attempt_timestamp BIGINT UNSIGNED NULL,
    failed_download_count INT UNSIGNED NOT NULL DEFAULT 0,
    priority BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY pending_transactions_uq (hash),
    INDEX pending_transactions_ix1 (priority) USING BTREE,
    INDEX pending_transactions_ix2 (failed_download_count) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE pending_transaction_data (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    pending_transaction_id INT UNSIGNED NOT NULL,
    data LONGBLOB NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY pending_transaction_data_uq (pending_transaction_id),
    FOREIGN KEY pending_transaction_data_fk (pending_transaction_id) REFERENCES pending_transactions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE pending_transactions_dependent_transactions (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    pending_transaction_id INT UNSIGNED NOT NULL,
    hash CHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY pending_transaction_prevout_uq (pending_transaction_id, hash),
    FOREIGN KEY pending_transaction_prevout_fk (pending_transaction_id) REFERENCES pending_transactions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE addresses (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    address VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY addresses_uq (address)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE blocks (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    hash CHAR(64) NOT NULL,
    previous_block_id INT UNSIGNED,
    block_height INT UNSIGNED NOT NULL,
    blockchain_segment_id INT UNSIGNED,
    merkle_root CHAR(64) NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT '1',
    timestamp BIGINT UNSIGNED NOT NULL,
    difficulty CHAR(8) NOT NULL,
    nonce INT UNSIGNED NOT NULL,
    chain_work CHAR(64) NOT NULL,
    byte_count INT UNSIGNED,
    PRIMARY KEY (id),
    UNIQUE KEY block_hash_uq (hash),
    UNIQUE KEY block_hash_uq2 (blockchain_segment_id, block_height),
    FOREIGN KEY block_previous_block_id_fk (previous_block_id) REFERENCES blocks (id),
    INDEX blocks_height_ix (block_height) USING BTREE,
    INDEX blocks_work_ix (chain_work) USING BTREE,
    INDEX blocks_work_ix2 (blockchain_segment_id, chain_work) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE blockchain_segments (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    parent_blockchain_segment_id INT UNSIGNED NULL,
    nested_set_left INT UNSIGNED NULL,
    nested_set_right INT UNSIGNED NULL,
    PRIMARY KEY (id),
    FOREIGN KEY blockchain_segments_parent_blockchain_segment_id (parent_blockchain_segment_id) REFERENCES blockchain_segments (id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

ALTER TABLE blocks ADD CONSTRAINT blocks_blockchain_segments_fk FOREIGN KEY (blockchain_segment_id) REFERENCES blockchain_segments (id);

CREATE TABLE transactions (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    hash CHAR(64) NOT NULL,
    version INT UNSIGNED NOT NULL,
    lock_time BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY transaction_hash_uq (hash)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE block_transactions (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    block_id INT UNSIGNED NOT NULL,
    transaction_id INT UNSIGNED NOT NULL,
    sort_order INT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY block_transactions_uq (block_id, transaction_id),
    FOREIGN KEY block_transactions_fk (block_id) REFERENCES blocks (id),
    FOREIGN KEY block_transactions_fk2 (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE unconfirmed_transactions (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    transaction_id INT UNSIGNED NOT NULL,
    timestamp BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY unconfirmed_transactions_uq (transaction_id),
    FOREIGN KEY unconfirmed_transactions_fk (transaction_id) REFERENCES transactions (id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE transaction_outputs (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    transaction_id INT UNSIGNED NOT NULL,
    `index` INT UNSIGNED NOT NULL,
    amount BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY transaction_output_tx_id_index_uq (transaction_id, `index`),
    FOREIGN KEY transaction_outputs_tx_id_fk (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE unspent_transaction_outputs (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    transaction_output_id INT UNSIGNED NOT NULL,
    transaction_hash CHAR(64) NOT NULL,
    `index` INT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY unspent_transaction_output_id_fk (transaction_output_id) REFERENCES transaction_outputs (id) ON DELETE CASCADE,
    INDEX transaction_outputs_spent_tx_id_ix (transaction_hash, `index`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE transaction_inputs (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    transaction_id INT UNSIGNED NOT NULL,
    previous_transaction_output_id INT UNSIGNED,
    sequence_number INT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY transaction_inputs_tx_id_prev_tx_id_uq (transaction_id, previous_transaction_output_id),
    FOREIGN KEY transaction_inputs_tx_id_fk (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE,
    FOREIGN KEY transaction_inputs_tx_out_fk (previous_transaction_output_id) REFERENCES transaction_outputs (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE script_types (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    type varchar(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY script_types_uq (type)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;
INSERT INTO script_types (id, type) VALUES (1, 'UNKNOWN'), (2, 'CUSTOM_SCRIPT'), (3, 'PAY_TO_PUBLIC_KEY'), (4, 'PAY_TO_PUBLIC_KEY_HASH'), (5, 'PAY_TO_SCRIPT_HASH');

CREATE TABLE locking_scripts (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    transaction_output_id INT UNSIGNED NOT NULL,
    script_type_id INT UNSIGNED NOT NULL,
    script BLOB NOT NULL,
    address_id INT UNSIGNED,
    PRIMARY KEY (id),
    UNIQUE KEY locking_scripts_uq (transaction_output_id),
    FOREIGN KEY locking_scripts_type_id_fk (script_type_id) REFERENCES script_types (id),
    FOREIGN KEY locking_scripts_output_id_fk (transaction_output_id) REFERENCES transaction_outputs (id) ON DELETE CASCADE,
    FOREIGN KEY locking_scripts_address_id_fk (address_id) REFERENCES addresses (id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE unlocking_scripts (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    transaction_input_id INT UNSIGNED NOT NULL,
    script BLOB NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY unlocking_scripts_uq (transaction_input_id),
    FOREIGN KEY unlocking_scripts_input_id_fk (transaction_input_id) REFERENCES transaction_inputs (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE address_processor_queue (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    locking_script_id INT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY address_processor_queue_uq (locking_script_id),
    FOREIGN KEY address_processor_queue_fk (locking_script_id) REFERENCES locking_scripts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE hosts (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    host VARCHAR(255) NOT NULL,
    is_banned TINYINT(1) UNSIGNED NOT NULL DEFAULT 0,
    banned_timestamp BIGINT UNSIGNED,
    PRIMARY KEY (id),
    UNIQUE KEY hosts_uq (host)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE nodes (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    host_id INT UNSIGNED NOT NULL,
    port INT UNSIGNED NOT NULL,
    first_seen_timestamp BIGINT UNSIGNED NOT NULL,
    last_seen_timestamp BIGINT UNSIGNED NOT NULL,
    connection_count INT UNSIGNED NOT NULL DEFAULT 1,
    last_handshake_timestamp BIGINT UNSIGNED,
    user_agent varchar(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY nodes_uq (host_id, port),
    FOREIGN KEY nodes_host_id_fk (host_id) REFERENCES hosts (id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE node_features (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    node_id INT UNSIGNED NOT NULL,
    feature VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY node_features_uq (node_id, feature),
    FOREIGN KEY node_features_fk (node_id) REFERENCES nodes (id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE node_blocks_inventory (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    node_id INT UNSIGNED NOT NULL,
    pending_block_id INT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY node_blocks_uq (node_id, pending_block_id),
    FOREIGN KEY node_blocks_node_id_fk (node_id) REFERENCES nodes (id),
    FOREIGN KEY node_blocks_tx_fk (pending_block_id) REFERENCES pending_blocks (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE node_transactions_inventory (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    node_id INT UNSIGNED NOT NULL,
    pending_transaction_id INT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY node_transactions_uq (node_id, pending_transaction_id),
    FOREIGN KEY node_transactions_node_id_fk (node_id) REFERENCES nodes (id),
    FOREIGN KEY node_transactions_tx_fk (pending_transaction_id) REFERENCES pending_transactions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

INSERT INTO metadata (version, timestamp) VALUES (1, UNIX_TIMESTAMP());
