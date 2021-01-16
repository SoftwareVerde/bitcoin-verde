-- Core Blockchain Tables

CREATE TABLE IF NOT EXISTS "blockchain_segments" (
	"id" INTEGER NOT NULL,
	"parent_blockchain_segment_id" INTEGER NULL,
	"nested_set_left" INTEGER NULL,
	"nested_set_right" INTEGER NULL,
	PRIMARY KEY ("id"),
	FOREIGN KEY("parent_blockchain_segment_id") REFERENCES "blockchain_segments" ("id") ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS "blocks" (
	"id" INTEGER NOT NULL,
	"hash" BLOB NOT NULL,
	"previous_block_id" INTEGER NULL,
	"block_height" INTEGER NOT NULL,
	"blockchain_segment_id" INTEGER NULL,
	"merkle_root" BLOB NOT NULL,
	"version" INTEGER NOT NULL DEFAULT 1,
	"timestamp" BIGINT NOT NULL,
	"median_block_time" BIGINT NOT NULL,
	"difficulty" BLOB NOT NULL,
	"nonce" INTEGER NOT NULL,
	"chain_work" BLOB NOT NULL,
	"has_transactions" TINYINT NULL DEFAULT 0,
	"byte_count" INTEGER NULL,
	PRIMARY KEY ("id"),
	FOREIGN KEY("blockchain_segment_id") REFERENCES "blockchain_segments" ("id") ON UPDATE RESTRICT ON DELETE RESTRICT,
	FOREIGN KEY("previous_block_id") REFERENCES "blocks" ("id") ON UPDATE RESTRICT ON DELETE RESTRICT
);

-- SPV, replaces full node `transactions` table.
CREATE TABLE IF NOT EXISTS "transactions" (
	"id" INTEGER NOT NULL,
	"hash" BLOB NOT NULL,
	"slp_validity" VARCHAR(255) NULL DEFAULT NULL,
	PRIMARY KEY ("id")
);

-- SPV, replaces full node `block_transactions` table.
CREATE TABLE IF NOT EXISTS "block_transactions" (
	"id" INTEGER NOT NULL,
	"block_id" INTEGER NOT NULL,
	"transaction_id" INTEGER NOT NULL,
	PRIMARY KEY ("id"),
	FOREIGN KEY("block_id") REFERENCES "blocks" ("id") ON UPDATE RESTRICT ON DELETE RESTRICT,
	FOREIGN KEY("transaction_id") REFERENCES "transactions" ("id") ON UPDATE RESTRICT ON DELETE CASCADE
);

-- SPV, new table.
CREATE TABLE IF NOT EXISTS "block_merkle_trees" (
	"id" INTEGER NOT NULL,
	"block_id" INTEGER NOT NULL,
	"merkle_tree_data" BLOB NOT NULL,
	PRIMARY KEY ("id"),
	FOREIGN KEY("block_id") REFERENCES "blocks" ("id") ON UPDATE RESTRICT ON DELETE CASCADE
);

-- SPV, new table.
CREATE TABLE IF NOT EXISTS "transaction_data" (
	"id" INTEGER NOT NULL,
	"transaction_id" INTEGER NOT NULL,
	"data" BLOB NOT NULL,
	PRIMARY KEY ("id"),
	FOREIGN KEY("transaction_id") REFERENCES "transactions" ("id") ON UPDATE RESTRICT ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS "invalid_blocks" (
	"id" INTEGER NOT NULL,
	"hash" BLOB NOT NULL,
	"process_count" INTEGER NOT NULL DEFAULT 0,
	PRIMARY KEY ("id")
);

-- Blockchain Head/Tip Performance Tables

CREATE VIEW IF NOT EXISTS "head_block_header" AS
    SELECT
        blocks.id, blocks.hash, blocks.previous_block_id, blocks.block_height, blocks.blockchain_segment_id, blocks.chain_work
    FROM
        blocks
        INNER JOIN ( SELECT chain_work FROM blocks ORDER BY chain_work DESC LIMIT 1 ) AS best_block_header_work
            ON (blocks.chain_work = best_block_header_work.chain_work)
    ORDER BY
        blocks.id ASC
    LIMIT 1
;

CREATE VIEW IF NOT EXISTS "head_block" AS
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
;

-- UTXO Tables

CREATE TABLE IF NOT EXISTS "committed_unspent_transaction_outputs" (
	"transaction_hash" BLOB NOT NULL,
	"index" INTEGER NOT NULL,
	"is_spent" TINYINT NOT NULL,
	"block_height" INTEGER NOT NULL,
	PRIMARY KEY ("transaction_hash", "index")
);

-- Unconfirmed Transaction (Mempool) Tables

CREATE TABLE IF NOT EXISTS "unconfirmed_transactions" (
	"transaction_id" INTEGER NOT NULL,
	"version" INTEGER NOT NULL,
	"lock_time" BIGINT NOT NULL,
	"timestamp" BIGINT NOT NULL,
	PRIMARY KEY ("transaction_id"),
	FOREIGN KEY("transaction_id") REFERENCES "transactions" ("id") ON UPDATE RESTRICT ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS "unconfirmed_transaction_outputs" (
	"id" INTEGER NOT NULL,
	"transaction_id" INTEGER NOT NULL,
	"index" INTEGER NOT NULL,
	"amount" BIGINT NOT NULL,
	"locking_script" BLOB NOT NULL,
	PRIMARY KEY ("id"),
	FOREIGN KEY("transaction_id") REFERENCES "unconfirmed_transactions" ("transaction_id") ON UPDATE RESTRICT ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS "unconfirmed_transaction_inputs" (
	"id" INTEGER NOT NULL,
	"transaction_id" INTEGER NOT NULL,
	"index" INTEGER NOT NULL,
	"previous_transaction_hash" BLOB NOT NULL,
	"previous_transaction_output_index" INTEGER NOT NULL,
	"sequence_number" INTEGER NOT NULL,
	"unlocking_script" BLOB NOT NULL,
	PRIMARY KEY ("id"),
	FOREIGN KEY("transaction_id") REFERENCES "unconfirmed_transactions" ("transaction_id") ON UPDATE RESTRICT ON DELETE CASCADE
);

-- Blockchain Download/Syncing Tables

CREATE TABLE IF NOT EXISTS "pending_blocks" (
	"id" INTEGER NOT NULL,
	"hash" BLOB NOT NULL,
	"previous_block_hash" BLOB NULL,
	"timestamp" BIGINT NOT NULL,
	"last_download_attempt_timestamp" BIGINT NULL,
	"failed_download_count" INTEGER NOT NULL DEFAULT 0,
	"priority" BIGINT NOT NULL,
	"was_downloaded" TINYINT NOT NULL DEFAULT 0,
	PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "pending_transactions" (
	"id" INTEGER NOT NULL,
	"hash" BLOB NOT NULL,
	"timestamp" BIGINT NOT NULL,
	"last_download_attempt_timestamp" BIGINT NULL,
	"failed_download_count" INTEGER NOT NULL DEFAULT 0,
	"priority" BIGINT NOT NULL,
	PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "pending_transaction_data" (
	"id" INTEGER NOT NULL,
	"pending_transaction_id" INTEGER NOT NULL,
	"data" BLOB NOT NULL,
	PRIMARY KEY ("id"),
	FOREIGN KEY("pending_transaction_id") REFERENCES "pending_transactions" ("id") ON UPDATE RESTRICT ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS "pending_transactions_dependent_transactions" (
	"id" INTEGER NOT NULL,
	"pending_transaction_id" INTEGER NOT NULL,
	"hash" BLOB NOT NULL,
	PRIMARY KEY ("id"),
	FOREIGN KEY("pending_transaction_id") REFERENCES "pending_transactions" ("id") ON UPDATE RESTRICT ON DELETE CASCADE
);

-- Node/Network Tables

CREATE TABLE IF NOT EXISTS "hosts" (
	"id" INTEGER NOT NULL,
	"host" VARCHAR(255) NOT NULL,
	"is_banned" TINYINT NOT NULL DEFAULT 0,
	"banned_timestamp" BIGINT NULL,
	PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "nodes" (
	"id" INTEGER NOT NULL,
	"host_id" INTEGER NOT NULL,
	"port" INTEGER NOT NULL,
	"first_seen_timestamp" BIGINT NOT NULL,
	"last_seen_timestamp" BIGINT NOT NULL,
	"connection_count" INTEGER NOT NULL DEFAULT 1,
	"last_handshake_timestamp" BIGINT NULL,
	"user_agent" VARCHAR(255) NULL,
	"head_block_height" INTEGER UNSIGNED NOT NULL,
    "head_block_hash" BLOB NOT NULL,
	PRIMARY KEY ("id"),
	FOREIGN KEY("host_id") REFERENCES "hosts" ("id") ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS "node_features" (
	"id" INTEGER NOT NULL,
	"node_id" INTEGER NOT NULL,
	"feature" VARCHAR(255) NOT NULL,
	PRIMARY KEY ("id"),
	FOREIGN KEY("node_id") REFERENCES "nodes" ("id") ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS "node_blocks_inventory" (
	"id" INTEGER NOT NULL,
	"node_id" INTEGER NOT NULL,
	"hash" BLOB NOT NULL,
	PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "node_transactions_inventory" (
	"id" INTEGER NOT NULL,
	"node_id" INTEGER NOT NULL,
	"hash" BLOB NOT NULL,
	PRIMARY KEY ("id")
);

-- Optional Indexing Tables

CREATE TABLE IF NOT EXISTS "script_types" (
	"id" INTEGER NOT NULL,
	"type" VARCHAR(255) NOT NULL,
	PRIMARY KEY ("id")
);
INSERT INTO script_types (id, type) VALUES
    (1, 'UNKNOWN'), (2, 'CUSTOM_SCRIPT'), (3, 'PAY_TO_PUBLIC_KEY'), (4, 'PAY_TO_PUBLIC_KEY_HASH'), (5, 'PAY_TO_SCRIPT_HASH'),
    (6, 'SLP_GENESIS_SCRIPT'), (7, 'SLP_SEND_SCRIPT'), (8, 'SLP_MINT_SCRIPT'), (9, 'SLP_COMMIT_SCRIPT');

CREATE TABLE IF NOT EXISTS "indexed_transaction_outputs" (
	"transaction_id" INTEGER NOT NULL,
	"output_index" INTEGER NOT NULL,
	"amount" BIGINT NOT NULL,
	"address" BLOB NULL,
	"script_type_id" INTEGER NOT NULL DEFAULT 1,
	"slp_transaction_id" INTEGER NULL,
	"memo_action_type" BLOB NULL,
	PRIMARY KEY ("transaction_id", "output_index")
);

CREATE TABLE IF NOT EXISTS "indexed_transaction_inputs" (
	"transaction_id" INTEGER NOT NULL,
	"input_index" INTEGER NOT NULL,
	"spends_transaction_id" INTEGER NULL,
	"spends_output_index" INTEGER NULL,
	PRIMARY KEY ("transaction_id", "input_index")
);

CREATE TABLE IF NOT EXISTS "validated_slp_transactions" (
	"id" INTEGER NOT NULL,
	"transaction_id" INTEGER NOT NULL,
	"is_valid" TINYINT NOT NULL DEFAULT 0,
	PRIMARY KEY ("id"),
	FOREIGN KEY("transaction_id") REFERENCES "transactions" ("id") ON UPDATE RESTRICT ON DELETE CASCADE
);

-- Misc

CREATE TABLE IF NOT EXISTS "properties" (
	"key" VARCHAR(255) NOT NULL,
	"value" VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS "metadata" (
	"id" INTEGER NOT NULL,
	"version" INTEGER NOT NULL,
	"timestamp" BIGINT NOT NULL,
	PRIMARY KEY ("id")
);

INSERT INTO metadata (version, timestamp) VALUES (4, STRFTIME('%s', 'now'));

CREATE UNIQUE INDEX "block_merkle_trees_block_merkle_trees_uq" ON "block_merkle_trees" ("block_id");
CREATE INDEX "block_transactions_block_transactions_fk2" ON "block_transactions" ("transaction_id");
CREATE UNIQUE INDEX "block_transactions_block_transactions_uq" ON "block_transactions" ("block_id", "transaction_id");
CREATE INDEX "blockchain_segments_blockchain_segments_parent_blockchain_segment_id" ON "blockchain_segments" ("parent_blockchain_segment_id");
CREATE INDEX "blocks_blocks_height_ix" ON "blocks" ("block_height");
CREATE INDEX "blocks_blocks_work_ix" ON "blocks" ("has_transactions", "chain_work");
CREATE INDEX "blocks_blocks_work_ix2" ON "blocks" ("chain_work", "has_transactions", "id");
CREATE INDEX "blocks_blocks_work_ix3" ON "blocks" ("blockchain_segment_id", "chain_work");
CREATE UNIQUE INDEX "blocks_block_hash_uq" ON "blocks" ("hash");
CREATE UNIQUE INDEX "blocks_block_hash_uq2" ON "blocks" ("blockchain_segment_id", "block_height");
CREATE INDEX "blocks_block_previous_block_id_fk" ON "blocks" ("previous_block_id");
CREATE UNIQUE INDEX "hosts_hosts_uq" ON "hosts" ("host");
CREATE INDEX "indexed_transaction_inputs_indexed_transaction_inputs_prevout_ix" ON "indexed_transaction_inputs" ("spends_transaction_id", "spends_output_index");
CREATE INDEX "indexed_transaction_outputs_indexed_transaction_outputs_addr_ix" ON "indexed_transaction_outputs" ("address");
CREATE INDEX "indexed_transaction_outputs_indexed_transaction_outputs_scripts_type_ix" ON "indexed_transaction_outputs" ("script_type_id");
CREATE INDEX "indexed_transaction_outputs_indexed_transaction_outputs_slp_tx_ix" ON "indexed_transaction_outputs" ("slp_transaction_id");
CREATE UNIQUE INDEX "invalid_blocks_invalid_blocks_uq" ON "invalid_blocks" ("hash");
CREATE UNIQUE INDEX "metadata_metadata_version_uq" ON "metadata" ("version");
CREATE INDEX "node_blocks_inventory_node_blocks_tx_ix" ON "node_blocks_inventory" ("hash");
CREATE UNIQUE INDEX "node_blocks_inventory_node_blocks_uq" ON "node_blocks_inventory" ("node_id", "hash");
CREATE UNIQUE INDEX "node_features_node_features_uq" ON "node_features" ("node_id", "feature");
CREATE INDEX "node_transactions_inventory_node_transactions_tx_ix" ON "node_transactions_inventory" ("hash");
CREATE UNIQUE INDEX "node_transactions_inventory_node_transactions_uq" ON "node_transactions_inventory" ("node_id", "hash");
CREATE UNIQUE INDEX "nodes_nodes_uq" ON "nodes" ("host_id", "port");
CREATE INDEX "pending_blocks_pending_blocks_ix1" ON "pending_blocks" ("priority");
CREATE INDEX "pending_blocks_pending_blocks_ix2" ON "pending_blocks" ("was_downloaded", "failed_download_count");
CREATE INDEX "pending_blocks_pending_blocks_ix3" ON "pending_blocks" ("previous_block_hash");
CREATE UNIQUE INDEX "pending_blocks_pending_blocks_uq" ON "pending_blocks" ("hash");
CREATE UNIQUE INDEX "pending_transaction_data_pending_transaction_data_uq" ON "pending_transaction_data" ("pending_transaction_id");
CREATE INDEX "pending_transactions_pending_transactions_ix1" ON "pending_transactions" ("priority");
CREATE INDEX "pending_transactions_pending_transactions_ix2" ON "pending_transactions" ("failed_download_count");
CREATE UNIQUE INDEX "pending_transactions_pending_transactions_uq" ON "pending_transactions" ("hash");
CREATE UNIQUE INDEX "pending_transactions_dependent_transactions_pending_transaction_prevout_uq" ON "pending_transactions_dependent_transactions" ("pending_transaction_id", "hash");
CREATE UNIQUE INDEX "properties_properties_uq" ON "properties" ("key");
CREATE UNIQUE INDEX "script_types_script_types_uq" ON "script_types" ("type");
CREATE UNIQUE INDEX "transaction_data_transaction_data_uq" ON "transaction_data" ("transaction_id");
CREATE UNIQUE INDEX "transactions_transactions_uq" ON "transactions" ("hash");
CREATE INDEX "unconfirmed_transaction_inputs_transaction_inputs_tx_id_fk" ON "unconfirmed_transaction_inputs" ("transaction_id");
CREATE INDEX "unconfirmed_transaction_inputs_unconfirmed_transaction_inputs_tx_hash_ix" ON "unconfirmed_transaction_inputs" ("previous_transaction_hash", "previous_transaction_output_index");
CREATE UNIQUE INDEX "unconfirmed_transaction_outputs_unconfirmed_transaction_output_tx_id_index_uq" ON "unconfirmed_transaction_outputs" ("transaction_id", "index");
CREATE INDEX "unspent_transaction_outputs_utxo_block_height_ix" ON "unspent_transaction_outputs" ("block_height");
CREATE UNIQUE INDEX "validated_slp_transactions_valid_slp_transactions_uq" ON "validated_slp_transactions" ("transaction_id");
CREATE UNIQUE INDEX "properties_uq" ON "properties" ("key");
