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

CREATE TABLE IF NOT EXISTS "node_transactions_inventory" (
	"id" INTEGER NOT NULL,
	"node_id" INTEGER NOT NULL,
	"hash" BLOB NOT NULL,
	PRIMARY KEY ("id")
);

-- Misc

CREATE TABLE IF NOT EXISTS "properties" (
	"id" INTEGER NOT NULL,
	"key" VARCHAR(255) NOT NULL,
	"value" VARCHAR(255) NOT NULL,
	PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "metadata" (
	"id" INTEGER NOT NULL,
	"version" INTEGER NOT NULL,
	"timestamp" BIGINT NOT NULL,
	PRIMARY KEY ("id")
);

INSERT INTO metadata (version, timestamp) VALUES (5, STRFTIME('%s', 'now'));

CREATE INDEX "blockchain_segments_blockchain_segments_parent_blockchain_segment_id" ON "blockchain_segments" ("parent_blockchain_segment_id");
CREATE INDEX "blocks_blocks_height_ix" ON "blocks" ("block_height");
CREATE INDEX "blocks_blocks_work_ix" ON "blocks" ("has_transactions", "chain_work");
CREATE INDEX "blocks_blocks_work_ix2" ON "blocks" ("chain_work", "has_transactions", "id");
CREATE INDEX "blocks_blocks_work_ix3" ON "blocks" ("blockchain_segment_id", "chain_work");
CREATE UNIQUE INDEX "blocks_block_hash_uq" ON "blocks" ("hash");
CREATE UNIQUE INDEX "blocks_block_hash_uq2" ON "blocks" ("blockchain_segment_id", "block_height");
CREATE INDEX "blocks_block_previous_block_id_fk" ON "blocks" ("previous_block_id");
CREATE INDEX "blocks_block_blockchain_segment_id_fk" ON "blocks" ("blockchain_segment_id");
CREATE UNIQUE INDEX "transactions_transactions_uq" ON "transactions" ("hash");
CREATE INDEX "block_transactions_block_transactions_fk2" ON "block_transactions" ("transaction_id");
CREATE UNIQUE INDEX "block_transactions_block_transactions_uq" ON "block_transactions" ("block_id", "transaction_id");
CREATE UNIQUE INDEX "block_merkle_trees_block_merkle_trees_uq" ON "block_merkle_trees" ("block_id");
CREATE UNIQUE INDEX "transaction_data_transaction_data_uq" ON "transaction_data" ("transaction_id");
CREATE UNIQUE INDEX "invalid_blocks_invalid_blocks_uq" ON "invalid_blocks" ("hash");
CREATE UNIQUE INDEX "hosts_hosts_uq" ON "hosts" ("host");
CREATE UNIQUE INDEX "nodes_nodes_uq" ON "nodes" ("host_id", "port");
CREATE INDEX "node_head_block_ix" ON "nodes" ("head_block_height", "head_block_hash");
CREATE UNIQUE INDEX "node_features_node_features_uq" ON "node_features" ("node_id", "feature");
CREATE INDEX "node_transactions_inventory_node_transactions_tx_ix" ON "node_transactions_inventory" ("hash");
CREATE UNIQUE INDEX "node_transactions_inventory_node_transactions_uq" ON "node_transactions_inventory" ("node_id", "hash");
CREATE UNIQUE INDEX "properties_key_uq" ON "properties" ("key");
CREATE UNIQUE INDEX "metadata_metadata_version_uq" ON "metadata" ("version");
