package com.softwareverde.bitcoin.server.module.node.database.block.spv;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTree;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTreeDeflater;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTreeInflater;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegment;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.util.Util;

public class SpvBlockDatabaseManager implements BlockDatabaseManager {
    protected final DatabaseManager _databaseManager;

    protected Long _getPartialMerkleTreeId(final BlockId blockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM block_merkle_trees WHERE block_id = ?")
                .setParameter(blockId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return row.getLong("id");
    }

    public SpvBlockDatabaseManager(final DatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    @Override
    public BlockId getHeadBlockId() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT blocks.id, blocks.hash FROM blocks INNER JOIN block_merkle_trees ON blocks.id = block_merkle_trees.block_id ORDER BY blocks.chain_work DESC LIMIT 1")
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockId.wrap(row.getLong("id"));
    }

    public BlockId selectNextIncompleteBlock(final Long minBlockHeight) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();

        final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
        final BlockchainSegment blockchainSegment = blockchainDatabaseManager.getBlockchainSegment(blockchainSegmentId);
        if (blockchainSegment == null) { return null; }

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT blocks.id, blocks.hash FROM blocks INNER JOIN blockchain_segments ON blockchain_segments.id = blocks.blockchain_segment_id WHERE blockchain_segments.nested_set_left <= ? AND blockchain_segments.nested_set_right >= ? AND NOT EXISTS (SELECT * FROM block_merkle_trees WHERE block_merkle_trees.block_id = blocks.id) AND blocks.block_height >= ? ORDER BY blocks.chain_work ASC LIMIT 1")
                .setParameter(blockchainSegment.nestedSetLeft)
                .setParameter(blockchainSegment.nestedSetRight)
                .setParameter(Util.coalesce(minBlockHeight))
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockId.wrap(row.getLong("id"));
    }

    @Override
    public Integer getTransactionCount(final BlockId blockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT block_id, merkle_tree_data FROM block_merkle_trees WHERE block_id = ?")
                .setParameter(blockId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);

        final PartialMerkleTreeInflater partialMerkleTreeInflater = new PartialMerkleTreeInflater();
        final PartialMerkleTree partialMerkleTree = partialMerkleTreeInflater.fromBytes(row.getBytes("merkle_tree_data"));

        return partialMerkleTree.getItemCount();
    }

    @Override
    public Boolean hasTransactions(final BlockId blockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT blocks.id FROM blocks INNER JOIN block_merkle_trees ON blocks.id = block_merkle_trees.block_id WHERE blocks.id = ?")
                .setParameter(blockId)
        );
        return (! rows.isEmpty());
    }

    @Override
    public Boolean hasTransactions(final Sha256Hash blockHash) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT blocks.id FROM blocks INNER JOIN block_merkle_trees ON blocks.id = block_merkle_trees.block_id WHERE blocks.hash = ?")
                .setParameter(blockHash)
        );
        return (! rows.isEmpty());
    }

    @Override
    public Sha256Hash getHeadBlockHash() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT blocks.id, blocks.hash FROM blocks INNER JOIN block_merkle_trees ON blocks.id = block_merkle_trees.block_id ORDER BY blocks.chain_work DESC LIMIT 1")
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return Sha256Hash.fromHexString(row.getString("hash"));
    }

    public void storePartialMerkleTree(final BlockId blockId, final PartialMerkleTree partialMerkleTree) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final PartialMerkleTreeDeflater partialMerkleTreeDeflater = new PartialMerkleTreeDeflater();

        final Long partialMerkleTreeId = _getPartialMerkleTreeId(blockId);
        if (partialMerkleTreeId != null) { return; }

        final ByteArray partialMerkleTreeBytes = partialMerkleTreeDeflater.toBytes(partialMerkleTree);
        databaseConnection.executeSql(
            new Query("INSERT INTO block_merkle_trees (block_id, merkle_tree_data) VALUES (?, ?)")
                .setParameter(blockId)
                .setParameter(partialMerkleTreeBytes.getBytes())
        );
    }

    public void addTransactionToBlock(final BlockId blockId, final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        databaseConnection.executeSql(
            new Query("INSERT IGNORE INTO block_transactions (block_id, transaction_id) VALUES (?, ?)")
                .setParameter(blockId)
                .setParameter(transactionId)
        );
    }

    @Override
    public List<TransactionId> getTransactionIds(final BlockId blockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, block_id, transaction_id FROM block_transactions WHERE block_id = ?")
                .setParameter(blockId)
        );

        final MutableList<TransactionId> transactionIds = new MutableList<TransactionId>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
            transactionIds.add(transactionId);
        }
        return transactionIds;
    }

    public List<BlockId> getBlockIdsWithTransactions() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT blocks.id, blocks.blockchain_segment_id FROM block_merkle_trees INNER JOIN blocks ON blocks.id = block_merkle_trees.block_id ORDER BY blocks.block_height ASC")
        );

        final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

        final MutableList<BlockId> blockIds = new MutableList<BlockId>(rows.size());
        for (final Row row : rows) {
            final BlockId blockId = BlockId.wrap(row.getLong("id"));
            final BlockchainSegmentId blockchainSegmentId = BlockchainSegmentId.wrap(row.getLong("blockchain_segment_id"));

            if (! blockchainDatabaseManager.areBlockchainSegmentsConnected(headBlockchainSegmentId, blockchainSegmentId, BlockRelationship.ANY)) { continue; }
            blockIds.add(blockId);
        }
        return blockIds;
    }
}
