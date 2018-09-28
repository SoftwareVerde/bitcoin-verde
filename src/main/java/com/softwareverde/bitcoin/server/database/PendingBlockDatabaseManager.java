package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlock;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

public class PendingBlockDatabaseManager {
    protected final SystemTime _systemTime = new SystemTime();
    protected final MysqlDatabaseConnection _databaseConnection;

    protected PendingBlockId _getPendingBlockId(final Sha256Hash blockHash) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM pending_blocks WHERE hash = ?")
                .setParameter(blockHash)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return PendingBlockId.wrap(row.getLong("id"));
    }

    protected List<PendingBlockId> _getPendingBlockIdsWithPreviousBlockHash(final Sha256Hash blockHash) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM pending_blocks WHERE previous_block_hash = ?")
                .setParameter(blockHash)
        );
        if (rows.isEmpty()) { return new MutableList<PendingBlockId>(); }

        final ImmutableListBuilder<PendingBlockId> listBuilder = new ImmutableListBuilder<PendingBlockId>(rows.size());
        for (final Row row : rows) {
            final PendingBlockId pendingBlockId = PendingBlockId.wrap(row.getLong("id"));
            listBuilder.add(pendingBlockId);
        }
        return listBuilder.build();
    }

    protected PendingBlockId _insertPendingBlock(final Sha256Hash blockHash, final Sha256Hash previousBlockHash) throws DatabaseException {
        final Long currentTimestamp = _systemTime.getCurrentTimeInSeconds();
        final Long priority = currentTimestamp;
        final Long pendingBlockId = _databaseConnection.executeSql(
            new Query("INSERT IGNORE INTO pending_blocks (hash, previous_block_hash, timestamp, priority) VALUES (?, ?, ?, ?)")
                .setParameter(blockHash)
                .setParameter(previousBlockHash)
                .setParameter(currentTimestamp)
                .setParameter(priority)
        );

        if (pendingBlockId == 0) {
            // The insert was ignored, so return the existing row.  This logic is necessary to prevent a race condition due to PendingBlockDatabaseManager not locking...
            return _getPendingBlockId(blockHash);
        }

        return PendingBlockId.wrap(pendingBlockId);
    }

    protected void _updatePendingBlockPreviousHash(final PendingBlockId pendingBlockId, final Sha256Hash blockHash, final Sha256Hash previousBlockHash) throws DatabaseException {
        final Long currentTimestamp = _systemTime.getCurrentTimeInSeconds();

        _databaseConnection.executeSql(
            new Query("UPDATE pending_blocks SET hash = ?, previous_block_hash = ?, timestamp = ? WHERE id = ?")
                .setParameter(blockHash)
                .setParameter(previousBlockHash)
                .setParameter(currentTimestamp)
                .setParameter(pendingBlockId)
        );
    }

    protected void _insertPendingBlockData(final PendingBlockId pendingBlockId, final ByteArray blockData) throws DatabaseException {
        _databaseConnection.executeSql(
            new Query("INSERT IGNORE INTO pending_block_data (pending_block_id, data) VALUES (?, ?)")
                .setParameter(pendingBlockId)
                .setParameter(blockData.getBytes())
        );
    }

    protected void _deletePendingBlock(final PendingBlockId pendingBlockId) throws DatabaseException {
        _databaseConnection.executeSql(
            new Query("DELETE FROM pending_block_data WHERE pending_block_id = ?")
                .setParameter(pendingBlockId)
        );

        _databaseConnection.executeSql(
            new Query("DELETE FROM pending_blocks WHERE id = ?")
                .setParameter(pendingBlockId)
        );
    }

    protected Boolean _hasBlockData(final PendingBlockId pendingBlockId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM pending_block_data WHERE pending_block_id = ?")
                .setParameter(pendingBlockId)
        );
        return (rows.size() > 0);
    }

    protected ByteArray _getBlockData(final PendingBlockId pendingBlockId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, data FROM pending_block_data WHERE pending_block_id = ?")
                .setParameter(pendingBlockId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return MutableByteArray.wrap(row.getBytes("data"));
    }

    protected PendingBlock _getPendingBlock(final PendingBlockId pendingBlockId, final Boolean includeDataIfAvailable) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, hash, previous_block_hash FROM pending_blocks WHERE id = ?")
                .setParameter(pendingBlockId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Sha256Hash blockHash = Sha256Hash.fromHexString(row.getString("hash"));
        final Sha256Hash previousBlockHash = Sha256Hash.fromHexString(row.getString("previous_block_hash"));
        final ByteArray blockData;
        {
            if (includeDataIfAvailable) {
                blockData = _getBlockData(pendingBlockId);
            }
            else {
                blockData = null;
            }
        }

        return new PendingBlock(blockHash, previousBlockHash, blockData);
    }

    public PendingBlockDatabaseManager(final MysqlDatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    public PendingBlockId getPendingBlockId(final Sha256Hash blockHash) throws DatabaseException {
        return _getPendingBlockId(blockHash);
    }

    public Boolean hasBlockData(final PendingBlockId pendingBlockId) throws DatabaseException {
        return _hasBlockData(pendingBlockId);
    }

    public Boolean pendingBlockExists(final Sha256Hash blockHash) throws DatabaseException {
        final PendingBlockId pendingBlockId = _getPendingBlockId(blockHash);
        return (pendingBlockId != null);
    }

    public List<PendingBlockId> getPendingBlockIdsWithPreviousBlockHash(final Sha256Hash previousBlockHash) throws DatabaseException {
        return _getPendingBlockIdsWithPreviousBlockHash(previousBlockHash);
    }

    public PendingBlockId insertBlockHash(final Sha256Hash blockHash) throws DatabaseException {
        return _insertPendingBlock(blockHash, null);
    }

    public PendingBlockId storeBlockHash(final Sha256Hash blockHash) throws DatabaseException {
        final PendingBlockId existingPendingBlockId = _getPendingBlockId(blockHash);
        if (existingPendingBlockId != null) { return existingPendingBlockId; }

        return _insertPendingBlock(blockHash, null);
    }

    public PendingBlockId storeBlock(final Block block) throws DatabaseException {
        final Sha256Hash blockHash = block.getHash();
        final Sha256Hash previousBlockHash = block.getPreviousBlockHash();

        final PendingBlockId pendingBlockId;
        {
            final PendingBlockId existingPendingBlockId = _getPendingBlockId(blockHash);
            if (existingPendingBlockId != null) {
                _updatePendingBlockPreviousHash(existingPendingBlockId, blockHash, previousBlockHash);
                pendingBlockId = existingPendingBlockId;
            }
            else {
                pendingBlockId = _insertPendingBlock(blockHash, previousBlockHash);
            }
        }

        final BlockDeflater blockDeflater = new BlockDeflater();
        _insertPendingBlockData(pendingBlockId, blockDeflater.toBytes(block));
        return pendingBlockId;
    }

    public List<PendingBlockId> selectIncompletePendingBlocks(final Integer maxCount) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT pending_blocks.id FROM pending_blocks LEFT OUTER JOIN pending_block_data ON pending_blocks.id = pending_block_data.pending_block_id WHERE pending_block_data.id IS NULL ORDER BY priority ASC, id ASC LIMIT " + Util.coalesce(maxCount, Integer.MAX_VALUE))
        );

        final ImmutableListBuilder<PendingBlockId> pendingBlockIdsBuilder = new ImmutableListBuilder<PendingBlockId>(rows.size());
        for (final Row row : rows) {
            pendingBlockIdsBuilder.add(PendingBlockId.wrap(row.getLong("id")));
        }
        return pendingBlockIdsBuilder.build();
    }

    public PendingBlockId selectCandidatePendingBlockId() throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT pending_blocks.id FROM pending_blocks INNER JOIN pending_block_data ON pending_blocks.id = pending_block_data.pending_block_id INNER JOIN blocks ON blocks.hash = pending_blocks.previous_block_hash INNER JOIN block_transactions ON block_transactions.block_id = blocks.id GROUP BY block_transactions.block_id ORDER BY pending_blocks.priority ASC LIMIT 1")
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return PendingBlockId.wrap(row.getLong("id"));
    }

    public Sha256Hash getPendingBlockHash(final PendingBlockId pendingBlockId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, hash FROM pending_blocks WHERE id = ?")
                .setParameter(pendingBlockId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return Sha256Hash.fromHexString(row.getString("hash"));
    }

    public void incrementFailedDownloadCount(final PendingBlockId pendingBlockId) throws DatabaseException {
        _databaseConnection.executeSql(
            new Query("UPDATE pending_blocks SET failed_download_count = failed_download_count + 1, priority = priority + 60 WHERE id = ?")
                .setParameter(pendingBlockId)
        );
    }

    public void setPriority(final PendingBlockId pendingBlockId, final Long priority) throws DatabaseException {
        _databaseConnection.executeSql(
            new Query("UPDATE pending_blocks SET priority = ? WHERE id = ?")
                .setParameter(priority)
                .setParameter(pendingBlockId)
        );
    }

    public void purgeFailedPendingBlocks(final Integer maxFailedDownloadCount) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM pending_blocks WHERE failed_download_count > ?")
                .setParameter(maxFailedDownloadCount)
        );

        for (final Row row : rows) {
            final PendingBlockId pendingBlockId = PendingBlockId.wrap(row.getLong("id"));
            Logger.log("Deleting Failed Pending Block: " + pendingBlockId);
            _deletePendingBlock(pendingBlockId);
        }
    }

    public PendingBlock getPendingBlock(final PendingBlockId pendingBlockId) throws DatabaseException {
        return _getPendingBlock(pendingBlockId, true);
    }

    public void deletePendingBlock(final PendingBlockId pendingBlockId) throws DatabaseException {
        _deletePendingBlock(pendingBlockId);
    }
}
