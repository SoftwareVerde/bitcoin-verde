CREATE TABLE addresses (
    id int unsigned NOT NULL AUTO_INCREMENT,
    address char(34) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY addresses_uq (address)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE blocks (
    id int unsigned NOT NULL AUTO_INCREMENT,
    hash char(64) NOT NULL,
    previous_block_id int unsigned,
    block_height int unsigned NOT NULL,
    block_chain_segment_id int unsigned,
    merkle_root char(64) NOT NULL,
    version int unsigned NOT NULL DEFAULT '1',
    timestamp bigint unsigned NOT NULL,
    difficulty char(8) NOT NULL,
    nonce bigint unsigned NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY block_hash_uq (hash),
    UNIQUE KEY block_hash_uq2 (block_chain_segment_id, block_height),
    FOREIGN KEY block_previous_block_id_fk (previous_block_id) REFERENCES blocks (id),
    INDEX block_timestamp_ix (timestamp) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE block_chain_segments (
    id int unsigned NOT NULL AUTO_INCREMENT,
    head_block_id int unsigned NOT NULL,
    tail_block_id int unsigned NOT NULL,
    block_height int unsigned NOT NULL,
    block_count int unsigned NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY block_chain_segments_block_ids_uq (head_block_id, tail_block_id),
    FOREIGN KEY block_chain_segments_head_block_id_ix (head_block_id) REFERENCES blocks (id),
    FOREIGN KEY block_chain_segments_tail_block_id_ix (tail_block_id) REFERENCES blocks (id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

ALTER TABLE blocks ADD CONSTRAINT blocks_block_chain_segments_fk FOREIGN KEY (block_chain_segment_id) REFERENCES block_chain_segments (id);

CREATE TABLE transactions (
    id int unsigned NOT NULL AUTO_INCREMENT,
    hash char(64) NOT NULL,
    block_id int unsigned,
    version int unsigned NOT NULL,
    lock_time bigint unsigned NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY transaction_hash_uq (hash, block_id),
    FOREIGN KEY transaction_block_id_ix (block_id) REFERENCES blocks (id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE transaction_outputs (
    id int unsigned NOT NULL AUTO_INCREMENT,
    transaction_id int unsigned NOT NULL,
    `index` int unsigned NOT NULL,
    amount bigint unsigned NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY transaction_output_tx_id_index_uq (transaction_id, `index`),
    FOREIGN KEY transaction_outputs_tx_id_ix (transaction_id) REFERENCES transactions (id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE transaction_inputs (
    id int unsigned NOT NULL AUTO_INCREMENT,
    transaction_id int unsigned NOT NULL,
    previous_transaction_output_id int unsigned,
    sequence_number int unsigned NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY transaction_inputs_tx_id_prev_tx_id_uq (transaction_id, previous_transaction_output_id),
    FOREIGN KEY transaction_inputs_tx_id_ix (transaction_id) REFERENCES transactions (id),
    FOREIGN KEY transaction_inputs_tx_out_ix (previous_transaction_output_id) REFERENCES transaction_outputs (id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE locking_scripts (
    id int unsigned NOT NULL AUTO_INCREMENT,
    type ENUM('UNKNOWN', 'PAY_TO_PUBLIC_KEY', 'PAY_TO_PUBLIC_KEY_HASH', 'PAY_TO_SCRIPT_HASH') NOT NULL,
    transaction_output_id int unsigned NOT NULL,
    script blob NULL,
    address_id int unsigned NULL,
    PRIMARY KEY (id),
    UNIQUE KEY locking_scripts_uq (transaction_output_id),
    FOREIGN KEY locking_scripts_output_id_ix (transaction_output_id) REFERENCES transaction_outputs (id),
    FOREIGN KEY locking_scripts_address_id_ix (address_id) REFERENCES addresses (id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE unlocking_scripts (
    id int unsigned NOT NULL AUTO_INCREMENT,
    transaction_input_id int unsigned NOT NULL,
    script blob NULL,
    PRIMARY KEY (id),
    UNIQUE KEY unlocking_scripts_uq (transaction_input_id),
    FOREIGN KEY unlocking_scripts_input_id_ix (transaction_input_id) REFERENCES transaction_inputs (id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

INSERT INTO metadata (version, timestamp) VALUES (1, UNIX_TIMESTAMP());
