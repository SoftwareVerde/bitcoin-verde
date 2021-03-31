-- Core Blockchain Tables

CREATE TABLE blockchain_segments (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    parent_blockchain_segment_id INT UNSIGNED,
    nested_set_left INT UNSIGNED,
    nested_set_right INT UNSIGNED,
    PRIMARY KEY (id),
    FOREIGN KEY blockchain_segments_parent_blockchain_segment_id (parent_blockchain_segment_id) REFERENCES blockchain_segments (id)
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

CREATE TABLE blocks (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    hash BINARY(32) NOT NULL,
    previous_block_id INT UNSIGNED,
    block_height INT UNSIGNED NOT NULL,
    blockchain_segment_id INT UNSIGNED,
    merkle_root BINARY(32) NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 1,
    timestamp BIGINT UNSIGNED NOT NULL,
    median_block_time BIGINT UNSIGNED NOT NULL COMMENT "The MedianBlockTime for this Block, which includes this Block's timestamp; the MedianTimePast (MTP) is the previous Block's MedianBlockTime.",
    difficulty BINARY(4) NOT NULL,
    nonce INT UNSIGNED NOT NULL,
    chain_work BINARY(32) NOT NULL,
    has_transactions TINYINT(1) UNSIGNED DEFAULT 0,
    byte_count INT UNSIGNED,
    PRIMARY KEY (id),
    UNIQUE KEY block_hash_uq (hash),
    UNIQUE KEY block_hash_uq2 (blockchain_segment_id, block_height),
    FOREIGN KEY block_previous_block_id_fk (previous_block_id) REFERENCES blocks (id),
    FOREIGN KEY blocks_blockchain_segments_fk (blockchain_segment_id) REFERENCES blockchain_segments (id),
    INDEX blocks_height_ix (block_height) USING BTREE,
    INDEX blocks_work_ix (has_transactions, chain_work DESC) USING BTREE,
    INDEX blocks_work_ix2 (chain_work DESC, has_transactions DESC, id ASC) USING BTREE,
    INDEX blocks_work_ix3 (blockchain_segment_id, chain_work DESC) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

CREATE TABLE transactions (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    hash BINARY(32) NOT NULL,
    byte_count INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY transaction_hash_uq (hash)
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

CREATE TABLE block_transactions (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    block_id INT UNSIGNED NOT NULL,
    transaction_id INT UNSIGNED NOT NULL,
    disk_offset BIGINT UNSIGNED NOT NULL,
    `index` INT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY block_transactions_uq (block_id, transaction_id),
    FOREIGN KEY block_transactions_fk (block_id) REFERENCES blocks (id),
    FOREIGN KEY block_transactions_fk2 (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

CREATE TABLE invalid_blocks (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    hash BINARY(32) NOT NULL,
    process_count INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY invalid_blocks_uq (hash)
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

-- Blockchain Head/Tip Performance Tables

CREATE VIEW head_block_header AS (
    SELECT
        blocks.id, blocks.hash, blocks.previous_block_id, blocks.block_height, blocks.blockchain_segment_id, blocks.chain_work
    FROM
        blocks
        INNER JOIN ( SELECT chain_work FROM blocks ORDER BY chain_work DESC LIMIT 1 ) AS best_block_header_work
            ON (blocks.chain_work = best_block_header_work.chain_work)
    ORDER BY
        blocks.id ASC
    LIMIT 1
);

CREATE VIEW head_block AS (
    SELECT
        blocks.id, blocks.hash, blocks.previous_block_id, blocks.block_height, blocks.blockchain_segment_id, blocks.chain_work
    FROM
        blocks
        INNER JOIN ( SELECT chain_work FROM blocks WHERE has_transactions = 1 ORDER BY chain_work DESC LIMIT 1 ) AS best_block_work
            ON blocks.chain_work = best_block_work.chain_work
    WHERE blocks.has_transactions = 1
    ORDER BY
        blocks.id ASC
    LIMIT 1
);

-- UTXO Tables

CREATE TABLE committed_unspent_transaction_outputs (
    transaction_hash BINARY(32) NOT NULL,
    `index` INT UNSIGNED NOT NULL,
    is_spent TINYINT(1) UNSIGNED NOT NULL DEFAULT 0,
    block_height INT UNSIGNED NOT NULL,
    PRIMARY KEY (transaction_hash, `index`)
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

-- Unconfirmed Transaction (Mempool) Tables

CREATE TABLE unconfirmed_transactions (
    transaction_id INT UNSIGNED NOT NULL,
    version INT UNSIGNED NOT NULL,
    lock_time BIGINT UNSIGNED NOT NULL,
    timestamp BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (transaction_id),
    FOREIGN KEY unconfirmed_transaction_transaction_fk (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

CREATE TABLE unconfirmed_transaction_outputs (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    transaction_id INT UNSIGNED NOT NULL,
    `index` INT UNSIGNED NOT NULL,
    amount BIGINT UNSIGNED NOT NULL,
    locking_script BLOB NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY unconfirmed_transaction_output_tx_id_index_uq (transaction_id, `index`),
    FOREIGN KEY unconfirmed_transaction_outputs_tx_id_fk (transaction_id) REFERENCES unconfirmed_transactions (transaction_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

CREATE TABLE unconfirmed_transaction_inputs (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    transaction_id INT UNSIGNED NOT NULL,
    `index` INT UNSIGNED NOT NULL,
    previous_transaction_hash BINARY(32) NOT NULL,
    previous_transaction_output_index INT UNSIGNED NOT NULL,
    sequence_number INT UNSIGNED NOT NULL,
    unlocking_script BLOB NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY transaction_inputs_tx_id_fk (transaction_id) REFERENCES unconfirmed_transactions (transaction_id) ON DELETE CASCADE,
    INDEX unconfirmed_transaction_inputs_tx_hash_ix (previous_transaction_hash, previous_transaction_output_index) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

-- Blockchain Download/Syncing Tables

CREATE TABLE pending_blocks (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    hash BINARY(32) NOT NULL,
    previous_block_hash BINARY(32) NULL,
    timestamp BIGINT UNSIGNED NOT NULL,
    last_download_attempt_timestamp BIGINT UNSIGNED,
    failed_download_count INT UNSIGNED NOT NULL DEFAULT 0,
    priority BIGINT NOT NULL,
    was_downloaded TINYINT(1) UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY pending_blocks_uq (hash),
    INDEX pending_blocks_ix1 (priority) USING BTREE,
    INDEX pending_blocks_ix2 (was_downloaded, failed_download_count) USING BTREE,
    INDEX pending_blocks_ix3 (previous_block_hash) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

CREATE TABLE pending_transactions (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    hash BINARY(32) NOT NULL,
    timestamp BIGINT UNSIGNED NOT NULL,
    last_download_attempt_timestamp BIGINT UNSIGNED,
    failed_download_count INT UNSIGNED NOT NULL DEFAULT 0,
    priority BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY pending_transactions_uq (hash),
    INDEX pending_transactions_ix1 (priority) USING BTREE,
    INDEX pending_transactions_ix2 (failed_download_count) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

CREATE TABLE pending_transaction_data (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    pending_transaction_id INT UNSIGNED NOT NULL,
    data LONGBLOB NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY pending_transaction_data_uq (pending_transaction_id),
    FOREIGN KEY pending_transaction_data_fk (pending_transaction_id) REFERENCES pending_transactions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

CREATE TABLE pending_transactions_dependent_transactions (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    pending_transaction_id INT UNSIGNED NOT NULL,
    hash BINARY(32) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY pending_transaction_prevout_uq (pending_transaction_id, hash),
    FOREIGN KEY pending_transaction_prevout_fk (pending_transaction_id) REFERENCES pending_transactions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

-- Node/Network Tables

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
    user_agent VARCHAR(255) NULL,
    head_block_height INT UNSIGNED NOT NULL,
    head_block_hash BINARY(32) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY nodes_uq (host_id, port),
    FOREIGN KEY nodes_host_id_fk (host_id) REFERENCES hosts (id),
    INDEX node_head_block_ix (head_block_height, head_block_hash) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE node_features (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    node_id INT UNSIGNED NOT NULL,
    feature VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY node_features_uq (node_id, feature),
    FOREIGN KEY node_features_fk (node_id) REFERENCES nodes (id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE node_transactions_inventory (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    node_id INT UNSIGNED NOT NULL,
    hash BINARY(32) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY node_transactions_uq (node_id, hash),
    FOREIGN KEY node_transactions_node_id_fk (node_id) REFERENCES nodes (id),
    INDEX node_transactions_tx_ix (hash) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

-- Optional Indexing Tables

CREATE TABLE script_types (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    type VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY script_types_uq (type)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

INSERT INTO script_types (id, type) VALUES
    (1, 'UNKNOWN'), (2, 'CUSTOM_SCRIPT'), (3, 'PAY_TO_PUBLIC_KEY'), (4, 'PAY_TO_PUBLIC_KEY_HASH'), (5, 'PAY_TO_SCRIPT_HASH'),
    (6, 'SLP_GENESIS_SCRIPT'), (7, 'SLP_SEND_SCRIPT'), (8, 'SLP_MINT_SCRIPT'), (9, 'SLP_COMMIT_SCRIPT'), (10, 'MEMO_SCRIPT');

CREATE TABLE indexed_transaction_outputs (
    transaction_id INT UNSIGNED NOT NULL,
    output_index INT UNSIGNED NOT NULL,
    amount BIGINT UNSIGNED NOT NULL,
    address BINARY(20),
    script_type_id INT UNSIGNED NOT NULL DEFAULT 1,
    slp_transaction_id INT UNSIGNED,
    memo_action_type BINARY(2),
    memo_action_identifier VARBINARY(255),
    PRIMARY KEY (transaction_id, output_index),
    INDEX indexed_transaction_outputs_addr_ix (address) USING BTREE,
    INDEX indexed_transaction_outputs_scripts_type_ix (script_type_id) USING BTREE,
    INDEX indexed_transaction_outputs_slp_tx_ix (slp_transaction_id) USING BTREE,
    INDEX indexed_transaction_outputs_memo_identifier (memo_action_identifier) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

CREATE TABLE indexed_transaction_inputs (
    transaction_id INT UNSIGNED NOT NULL,
    input_index INT UNSIGNED NOT NULL,
    spends_transaction_id INT UNSIGNED,
    spends_output_index INT UNSIGNED,
    PRIMARY KEY (transaction_id, input_index),
    INDEX indexed_transaction_inputs_prevout_ix (spends_transaction_id, spends_output_index) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

CREATE TABLE validated_slp_transactions (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    transaction_id INT UNSIGNED NOT NULL,
    is_valid TINYINT(1) UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY valid_slp_transactions_uq (transaction_id),
    FOREIGN KEY valid_slp_transactions_tx_id_fk (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

CREATE TABLE double_spend_proofs (
    hash BINARY(32) NOT NULL,
    transaction_id INT UNSIGNED NOT NULL,
    output_index INT UNSIGNED NOT NULL,
    data BLOB,
    PRIMARY KEY (hash),
    UNIQUE KEY dsproof_previous_output_ix (transaction_id, output_index)
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

-- Misc

CREATE TABLE properties (
    `key` VARCHAR(255) NOT NULL,
    `value` VARCHAR(255) NOT NULL,
    PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

INSERT INTO metadata (version, timestamp) VALUES (5, UNIX_TIMESTAMP());
