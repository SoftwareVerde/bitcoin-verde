CREATE TABLE blocks (
    id int unsigned NOT NULL AUTO_INCREMENT,
    hash varchar(64) NOT NULL,
    previous_block_id int unsigned,
    block_height int unsigned NOT NULL,
    block_chain_id int unsigned,
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

CREATE TABLE block_chains (
    id int unsigned NOT NULL AUTO_INCREMENT,
    head_block_id int unsigned NOT NULL,
    tail_block_id int unsigned NOT NULL,
    block_height int unsigned NOT NULL,
    block_count int unsigned NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY block_chains_block_ids_uq (head_block_id, tail_block_id),
    FOREIGN KEY block_chains_head_block_id_ix (head_block_id) REFERENCES blocks (id),
    FOREIGN KEY block_chains_tail_block_id_ix (tail_block_id) REFERENCES blocks (id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8;

ALTER TABLE blocks ADD CONSTRAINT blocks_block_chain_fk FOREIGN KEY (block_chain_id) REFERENCES block_chains (id);

CREATE TABLE transactions (
    id int unsigned NOT NULL AUTO_INCREMENT,
    hash varchar(64) NOT NULL,
    block_id int unsigned,
    version int NOT NULL,
    has_witness_data tinyint(1) NOT NULL,
    lock_time bigint unsigned NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY transaction_hash_uq (block_id, hash),
    FOREIGN KEY transaction_block_id_ix (block_id) REFERENCES blocks (id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8;

CREATE TABLE transaction_outputs (
    id int unsigned NOT NULL AUTO_INCREMENT,
    transaction_id int unsigned NOT NULL,
    `index` int unsigned NOT NULL,
    amount bigint unsigned NOT NULL,
    locking_script blob NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY transaction_output_tx_id_index_uq (transaction_id, `index`),
    FOREIGN KEY transaction_outputs_tx_id_ix (transaction_id) REFERENCES transactions (id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8;

CREATE TABLE transaction_inputs (
    id int unsigned NOT NULL AUTO_INCREMENT,
    transaction_id int unsigned NOT NULL,
    previous_transaction_output_id int unsigned,
    unlocking_script blob NOT NULL,
    sequence_number int unsigned NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY transaction_inputs_tx_id_prev_tx_id_uq (transaction_id, previous_transaction_output_id),
    FOREIGN KEY transaction_inputs_tx_id_ix (transaction_id) REFERENCES transactions (id),
    FOREIGN KEY transaction_inputs_tx_out_ix (previous_transaction_output_id) REFERENCES transaction_outputs (id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8;

INSERT INTO metadata (version, timestamp) VALUES (1, UNIX_TIMESTAMP());
