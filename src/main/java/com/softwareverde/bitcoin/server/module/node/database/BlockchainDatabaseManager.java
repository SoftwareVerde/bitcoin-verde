package com.softwareverde.bitcoin.server.module.node.database;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegment;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.util.Util;

public class BlockchainDatabaseManager {
    protected final DatabaseConnection _databaseConnection;
    protected final DatabaseManagerCache _databaseManagerCache;

    protected BlockchainSegmentId _calculateBlockchainSegment(final BlockId blockId) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(_databaseConnection, _databaseManagerCache);

        final BlockId parentBlockId = blockHeaderDatabaseManager.getAncestorBlockId(blockId, 1);
        if (parentBlockId == null) {
            final Sha256Hash blockHash = blockHeaderDatabaseManager.getBlockHash(blockId);
            if (! Util.areEqual(BlockHeader.GENESIS_BLOCK_HASH, blockHash)) { throw new DatabaseException("Invalid GenesisBlock hash: " + blockHash); }
            // Create the Genesis BlockchainSegment...
            return _createNewBlockchainSegment(null);
        }

        final BlockchainSegmentId parentBlockSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(parentBlockId);
        if (parentBlockSegmentId == null) { return null; }

        final Boolean blockIsOnlyChild = (blockHeaderDatabaseManager.getBlockDirectDescendantCount(parentBlockId) <= 1);
        if (blockIsOnlyChild) {
            // The blockchainSegment has no children, so it safe to create on the same segment...
            return parentBlockSegmentId;
        }

        // Eg: Inserting new contentious block, C', whose parent is B...
        //
        //           A    [#0]                   A    [#0]
        //           |                           |
        //           B                           B
        //           |                 [#1] +----+----+ [#3]
        //           C            ->        |         |
        //           |                      C         C'
        // [#1] +----+----+ [#2]            |
        //      |         |       [#4] +----+----+ [#5]
        //      D         D'           |         |
        //      |         |            D         D'
        //      E         E'           |         |
        //                             E         E'

        final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
        final Long parentBlockchainSegmentMaxBlockHeight;
        {
            final java.util.List<Row> rows = _databaseConnection.query(
                new Query("SELECT MAX(block_height) AS max_height FROM blocks WHERE blockchain_segment_id = ?")
                    .setParameter(parentBlockSegmentId)
            );
            if (rows.isEmpty()) { return null; }

            final Row row = rows.get(0);
            parentBlockchainSegmentMaxBlockHeight = row.getLong("max_height");
        }

        if (blockHeight <= parentBlockchainSegmentMaxBlockHeight) {
            _splitBlockchainSegment(parentBlockSegmentId, blockHeight);
        }

        return _createNewBlockchainSegment(parentBlockSegmentId);
    }

    protected BlockchainSegmentId _splitBlockchainSegment(final BlockchainSegmentId blockchainSegmentId, final Long blockHeight) throws DatabaseException {
        // (A)          -> (A) - (B)
        // (A) - (C)    -> (A) - (B) - (C)

        final BlockchainSegmentId newBlockchainSegmentId = _createNewBlockchainSegment(blockchainSegmentId);

        // Move the blocks after the contentious block height to the new segment...
        _databaseConnection.executeSql(
            new Query("UPDATE blocks SET blockchain_segment_id = ? WHERE blockchain_segment_id = ? AND block_height >= ?")
                .setParameter(newBlockchainSegmentId)
                .setParameter(blockchainSegmentId)
                .setParameter(blockHeight)
        );

        _databaseConnection.executeSql(
            new Query("UPDATE blockchain_segments SET parent_blockchain_segment_id = ? WHERE parent_blockchain_segment_id = ? AND id != ?")
                .setParameter(newBlockchainSegmentId)
                .setParameter(blockchainSegmentId)
                .setParameter(newBlockchainSegmentId)
        );

        return newBlockchainSegmentId;
    }

    protected BlockchainSegmentId _createNewBlockchainSegment(final BlockchainSegmentId parentBlockchainSegmentId) throws DatabaseException {
        if (parentBlockchainSegmentId == null) {
            final java.util.List<Row> rows = _databaseConnection.query(
                new Query("SELECT id FROM blockchain_segments WHERE parent_blockchain_segment_id IS NULL")
            );
            if (! rows.isEmpty()) {  throw new DatabaseException("Attempted to create more than one root BlockchainSegment."); }
        }

        final Long blockchainSegmentId = _databaseConnection.executeSql(
            new Query("INSERT INTO blockchain_segments (parent_blockchain_segment_id) VALUES (?)")
                .setParameter(parentBlockchainSegmentId)
        );

        return BlockchainSegmentId.wrap(blockchainSegmentId);
    }

    protected void _renumberBlockchainSegments() throws DatabaseException {
        final BlockchainSegmentId rootSegmentId = _getRootBlockchainSegmentId();
        _setLeftNumber(rootSegmentId, 1);
        final Integer endingNumber = _numberBlockchainSegmentChildren(rootSegmentId, 2);
        _setRightNumber(rootSegmentId, endingNumber);
    }

    protected BlockchainSegmentId _getRootBlockchainSegmentId() throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM blockchain_segments WHERE parent_blockchain_segment_id is NULL")
        );
        if (rows.size() != 1) { throw new DatabaseException("Invalid database state: " + rows.size() + " root BlockchainSegments found."); }

        final Row row = rows.get(0);
        return BlockchainSegmentId.wrap(row.getLong("id"));
    }

    protected Integer _numberBlockchainSegmentChildren(final BlockchainSegmentId blockchainSegmentId, final Integer startingNumber) throws DatabaseException {
        Integer number = startingNumber;
        final List<BlockchainSegmentId> childBlockchainSegmentIds = _getChildSegmentIds(blockchainSegmentId);
        for (final BlockchainSegmentId childBlockchainSegmentId : childBlockchainSegmentIds) {
            _setLeftNumber(childBlockchainSegmentId, number);
            number += 1;

            number = _numberBlockchainSegmentChildren(childBlockchainSegmentId, number);

            _setRightNumber(childBlockchainSegmentId, number);
            number += 1;
        }
        return number;
    }

    protected void _setLeftNumber(final BlockchainSegmentId blockchainSegmentId, final Integer value) throws DatabaseException {
        _databaseConnection.executeSql(
            new Query("UPDATE blockchain_segments SET nested_set_left = ? WHERE id = ?")
                .setParameter(value)
                .setParameter(blockchainSegmentId)
        );
    }

    protected void _setRightNumber(final BlockchainSegmentId blockchainSegmentId, final Integer value) throws DatabaseException {
        _databaseConnection.executeSql(
            new Query("UPDATE blockchain_segments SET nested_set_right = ? WHERE id = ?")
                .setParameter(value)
                .setParameter(blockchainSegmentId)
        );
    }

    protected List<BlockchainSegmentId> _getChildSegmentIds(final BlockchainSegmentId blockchainSegmentId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM blockchain_segments WHERE parent_blockchain_segment_id = ?")
                .setParameter(blockchainSegmentId)
        );

        final ImmutableListBuilder<BlockchainSegmentId> blockchainSegmentIds = new ImmutableListBuilder<BlockchainSegmentId>(rows.size());
        for (final Row row : rows) {
            blockchainSegmentIds.add(BlockchainSegmentId.wrap(row.getLong("id")));
        }
        return blockchainSegmentIds.build();
    }

    protected Boolean _areBlockchainSegmentsConnected(final BlockchainSegmentId blockchainSegmentId0, final BlockchainSegmentId blockchainSegmentId1, final BlockRelationship blockRelationship) throws DatabaseException {
        final Query query;
        switch (blockRelationship) {
            case ANCESTOR: {
                query = new Query("SELECT (A.nested_set_left <= B.nested_set_left AND A.nested_set_right >= B.nested_set_right) AS are_connected FROM (SELECT nested_set_left, nested_set_right FROM blockchain_segments WHERE id = ?) AS A, (SELECT nested_set_left, nested_set_right FROM blockchain_segments WHERE id = ?) AS B")
                    .setParameter(blockchainSegmentId0)
                    .setParameter(blockchainSegmentId1);
            } break;

            case DESCENDANT: {
                query = new Query("SELECT (A.nested_set_left >= B.nested_set_left AND A.nested_set_right <= B.nested_set_right) AS are_connected FROM (SELECT nested_set_left, nested_set_right FROM blockchain_segments WHERE id = ?) AS A, (SELECT nested_set_left, nested_set_right FROM blockchain_segments WHERE id = ?) AS B")
                    .setParameter(blockchainSegmentId0)
                    .setParameter(blockchainSegmentId1);
            } break;

            default: {
                query = new Query("SELECT (A.nested_set_left <= B.nested_set_left AND A.nested_set_right >= B.nested_set_right) OR (A.nested_set_left >= B.nested_set_left AND A.nested_set_right <= B.nested_set_right) AS are_connected FROM (SELECT nested_set_left, nested_set_right FROM blockchain_segments WHERE id = ?) AS A, (SELECT nested_set_left, nested_set_right FROM blockchain_segments WHERE id = ?) AS B")
                    .setParameter(blockchainSegmentId0)
                    .setParameter(blockchainSegmentId1);
            }
        }

        final java.util.List<Row> rows = _databaseConnection.query(query);
        if (rows.isEmpty()) { throw new DatabaseException("No blockchain segment matches returned."); }

        final Row row = rows.get(0);
        return row.getBoolean("are_connected");
    }

    public BlockchainDatabaseManager(final DatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache) {
        _databaseConnection = databaseConnection;
        _databaseManagerCache = databaseManagerCache;
    }

    public BlockchainSegment getBlockchainSegment(final BlockchainSegmentId blockchainSegmentId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT * FROM blockchain_segments WHERE id = ?")
                .setParameter(blockchainSegmentId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Long nestedSetLeft = row.getLong("nested_set_left");
        final Long nestedSetRight = row.getLong("nested_set_right");
        return new BlockchainSegment(blockchainSegmentId, nestedSetLeft, nestedSetRight);
    }

    public BlockchainSegmentId updateBlockchainsForNewBlock(final BlockId blockId) throws DatabaseException {
        if (! Thread.holdsLock(BlockHeaderDatabaseManager.MUTEX)) { throw new RuntimeException("Attempting to updateBlockchainsForNewBlock without obtaining lock."); }

        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(_databaseConnection, _databaseManagerCache);

        final BlockchainSegmentId existingBlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);
        if (existingBlockchainSegmentId != null) { return existingBlockchainSegmentId; }

        final BlockchainSegmentId blockchainSegmentId = _calculateBlockchainSegment(blockId);
        if (blockchainSegmentId == null) {
            final Sha256Hash blockHash = blockHeaderDatabaseManager.getBlockHash(blockId);
            throw new DatabaseException("Unable to update BlockchainSegment for Block: " + blockHash);
        }

        blockHeaderDatabaseManager.setBlockchainSegmentId(blockId, blockchainSegmentId);

        _renumberBlockchainSegments();

        return blockchainSegmentId;
    }

    public BlockchainSegmentId getHeadBlockchainSegmentId() throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, blockchain_segment_id FROM blocks ORDER BY chain_work DESC LIMIT 1")
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockchainSegmentId.wrap(row.getLong("blockchain_segment_id"));
    }

    public BlockId getHeadBlockIdOfBlockchainSegment(final BlockchainSegmentId blockchainSegmentId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM blocks WHERE blockchain_segment_id = ? ORDER BY chain_work DESC LIMIT 1")
                .setParameter(blockchainSegmentId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockId.wrap(row.getLong("id"));
    }

    public BlockchainSegmentId getHeadBlockchainSegmentIdOfBlockchainSegment(final BlockchainSegmentId blockchainSegmentId) throws DatabaseException {
        // NOTE: This query is optimized for use with an index on (blocks.blockchain_segment_id, blocks.chain_work) in order to prevent scanning the full table...
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT blocks.id, blocks.blockchain_segment_id FROM (SELECT nested_set_left, nested_set_right FROM blockchain_segments WHERE id = ?) AS A INNER JOIN (SELECT id, nested_set_left, nested_set_right FROM blockchain_segments) AS B ON (A.nested_set_left <= B.nested_set_left AND A.nested_set_right >= B.nested_set_right) INNER JOIN (SELECT blockchain_segment_id, MAX(blocks.chain_work) AS max_chain_work FROM blocks GROUP BY blocks.blockchain_segment_id) AS C ON (C.blockchain_segment_id = B.id) INNER JOIN blocks ON (blocks.blockchain_segment_id = C.blockchain_segment_id AND chain_work = C.max_chain_work) ORDER BY blocks.chain_work DESC LIMIT 1")
                .setParameter(blockchainSegmentId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockchainSegmentId.wrap(row.getLong("blockchain_segment_id"));
    }

    public Boolean areBlockchainSegmentsConnected(final BlockchainSegmentId blockchainSegmentId0, final BlockchainSegmentId blockchainSegmentId1, final BlockRelationship blockRelationship) throws DatabaseException {
        return _areBlockchainSegmentsConnected(blockchainSegmentId0, blockchainSegmentId1, blockRelationship);
    }
}
