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

-- SPV, replaces full node `transactions` table.
CREATE TABLE transactions (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    hash BINARY(32) NOT NULL,
    slp_validity VARCHAR(255) DEFAULT NULL, -- contains only the resolved state, tracking whether this is contested should happen elsewhere
    PRIMARY KEY (id),
    UNIQUE KEY transactions_uq (hash)
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

-- SPV, replaces full node `block_transactions` table.
CREATE TABLE block_transactions (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    block_id INT UNSIGNED NOT NULL,
    transaction_id INT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY block_transactions_fk (block_id) REFERENCES blocks (id),
    FOREIGN KEY block_transactions_fk2 (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE,
    UNIQUE KEY block_transactions_uq (block_id, transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

-- SPV, new table.
CREATE TABLE block_merkle_trees (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    block_id INT UNSIGNED NOT NULL,
    merkle_tree_data BINARY NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY block_merkle_tree_data_fk (block_id) REFERENCES blocks (id) ON DELETE CASCADE,
    UNIQUE KEY block_merkle_trees_uq (block_id)
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

-- SPV, new table.
CREATE TABLE transaction_data (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    transaction_id INT UNSIGNED NOT NULL,
    data LONGBLOB NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY transaction_data_fk (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE,
    UNIQUE KEY transaction_data_uq (transaction_id)
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

-- Misc

CREATE TABLE properties (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `key` VARCHAR(255) NOT NULL,
    `value` VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY properties_uq (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

INSERT INTO metadata (version, timestamp) VALUES (5, UNIX_TIMESTAMP());
