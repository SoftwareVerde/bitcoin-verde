CREATE INDEX "blocks_block_hash_uq" ON "blocks" ("hash");
CREATE INDEX "blocks_blocks_height_ix" ON "blocks" ("block_height");
CREATE INDEX "blocks_blocks_work_ix" ON "blocks" ("chain_work");
CREATE INDEX "blocks_blocks_work_ix2" ON "blocks" ("blockchain_segment_id","chain_work");