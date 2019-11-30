CREATE TABLE "blockchain_segments" (
    "id" INTEGER NOT NULL,
    "parent_blockchain_segment_id" INTEGER DEFAULT NULL,
    "nested_set_left" INTEGER DEFAULT NULL,
    "nested_set_right" INTEGER DEFAULT NULL,
    PRIMARY KEY ("id"),
    CONSTRAINT "blockchain_segments_parent_blockchain_segment_id" FOREIGN KEY ("parent_blockchain_segment_id") REFERENCES "blockchain_segments" ("id")
);

CREATE TABLE "blocks" (
    "id" INTEGER NOT NULL,
    "hash" char(64) NOT NULL,
    "previous_block_id" INTEGER DEFAULT NULL,
    "block_height" INTEGER NOT NULL,
    "blockchain_segment_id" INTEGER DEFAULT NULL,
    "merkle_root" char(64) NOT NULL,
    "version" INTEGER NOT NULL DEFAULT 1,
    "timestamp" INTEGER NOT NULL,
    "difficulty" char(8) NOT NULL,
    "nonce" INTEGER NOT NULL,
    "chain_work" char(64) NOT NULL,
    "byte_count" INTEGER DEFAULT NULL,
    PRIMARY KEY ("id"),
    CONSTRAINT "block_previous_block_id_fk" FOREIGN KEY ("previous_block_id") REFERENCES "blocks" ("id"),
    CONSTRAINT "blocks_blockchain_segments_fk" FOREIGN KEY ("blockchain_segment_id") REFERENCES "blockchain_segments" ("id"),
    UNIQUE("hash")
);

CREATE TABLE "block_merkle_trees" (
    "id" INTEGER NOT NULL,
    "block_id" INTEGER NOT NULL,
    "merkle_tree_data" BINARY NOT NULL,
    PRIMARY KEY ("id"),
    CONSTRAINT "block_merkle_tree_data_fk" FOREIGN KEY ("block_id") REFERENCES "blocks" ("id") ON DELETE CASCADE,
    UNIQUE("block_id")
);

CREATE TABLE "transactions" (
    "id" INTEGER NOT NULL,
    "hash" char(64) NOT NULL,
    "slp_validity" VARCHAR(255) DEFAULT NULL, -- contains only the "resolved" state, tracking whether this is contested should happen elsewhere
    PRIMARY KEY ("id"),
    UNIQUE("hash")
);

CREATE TABLE "transaction_data" (
    "id" INTEGER NOT NULL,
    "transaction_id" INTEGER NOT NULL,
    "data" LONGBLOB NOT NULL,
    PRIMARY KEY ("id"),
    CONSTRAINT "transaction_data_fk" FOREIGN KEY ("transaction_id") REFERENCES "transactions" ("id") ON DELETE CASCADE,
    UNIQUE("transaction_id")
);

CREATE TABLE "block_transactions" (
    "id" INTEGER NOT NULL,
    "block_id" INTEGER NOT NULL,
    "transaction_id" INTEGER NOT NULL,
    PRIMARY KEY ("id"),
    CONSTRAINT "block_transactions_fk" FOREIGN KEY ("block_id") REFERENCES "blocks" ("id"),
    CONSTRAINT "block_transactions_fk2" FOREIGN KEY ("transaction_id") REFERENCES "transactions" ("id") ON DELETE CASCADE,
    UNIQUE("block_id", "transaction_id")
);

CREATE TABLE "hosts" (
    "id" INTEGER NOT NULL,
    "host" varchar(255) NOT NULL,
    "is_banned" tinyint(1) NOT NULL DEFAULT 0,
    "banned_timestamp" bigint(20)  DEFAULT NULL,
    PRIMARY KEY ("id"),
    UNIQUE("host")
);

CREATE TABLE "nodes" (
    "id" INTEGER NOT NULL,
    "host_id" INTEGER NOT NULL,
    "port" INTEGER NOT NULL,
    "first_seen_timestamp" INTEGER NOT NULL,
    "last_seen_timestamp" INTEGER NOT NULL,
    "connection_count" INTEGER NOT NULL DEFAULT 1,
    "last_handshake_timestamp" INTEGER DEFAULT NULL,
    "user_agent" varchar(255) DEFAULT NULL,
    PRIMARY KEY ("id"),
    CONSTRAINT "nodes_host_id_fk" FOREIGN KEY ("host_id") REFERENCES "hosts" ("id"),
    UNIQUE("host_id", "port")
);

CREATE TABLE "node_features" (
    "id" INTEGER NOT NULL,
    "node_id" INTEGER NOT NULL,
    "feature" varchar(255) NOT NULL,
    PRIMARY KEY ("id"),
    CONSTRAINT "node_features_fk" FOREIGN KEY ("node_id") REFERENCES "nodes" ("id"),
    UNIQUE("node_id", "feature")
);

CREATE INDEX "nodes_nodes_uq" ON "nodes" ("host_id","port");
CREATE INDEX "hosts_hosts_uq" ON "hosts" ("host");
CREATE INDEX "blockchain_segments_blockchain_segments_parent_blockchain_segment_id" ON "blockchain_segments" ("parent_blockchain_segment_id");
CREATE INDEX "block_transactions_block_transactions_uq" ON "block_transactions" ("block_id","transaction_id");
CREATE INDEX "block_transactions_block_transactions_fk2" ON "block_transactions" ("transaction_id");
CREATE INDEX "node_features_node_features_uq" ON "node_features" ("node_id","feature");
CREATE INDEX "transactions_transaction_hash_uq" ON "transactions" ("hash");
CREATE INDEX "metadata_metadata_version_uq" ON "metadata" ("version");

CREATE INDEX "blocks_block_hash_uq2" ON "blocks" ("blockchain_segment_id","block_height");
CREATE INDEX "blocks_block_previous_block_id_fk" ON "blocks" ("previous_block_id");