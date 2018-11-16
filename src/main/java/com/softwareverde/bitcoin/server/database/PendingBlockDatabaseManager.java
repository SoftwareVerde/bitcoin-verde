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

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PendingBlockDatabaseManager {
    public static final ReentrantReadWriteLock.ReadLock READ_LOCK;
    public static final ReentrantReadWriteLock.WriteLock WRITE_LOCK;
    static {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        READ_LOCK = readWriteLock.readLock();
        WRITE_LOCK = readWriteLock.writeLock();
    }

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

    protected PendingBlockId _storePendingBlock(final Sha256Hash blockHash, final Sha256Hash previousBlockHash) throws DatabaseException {
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
            final PendingBlockId existingPendingBlockId = _getPendingBlockId(blockHash);
            if (previousBlockHash != null) {
                _updatePendingBlock(existingPendingBlockId, previousBlockHash);
            }
            return existingPendingBlockId;
        }

        return PendingBlockId.wrap(pendingBlockId);
    }

    protected void _updatePendingBlock(final PendingBlockId pendingBlockId, final Sha256Hash previousBlockHash) throws DatabaseException {
        final Long currentTimestamp = _systemTime.getCurrentTimeInSeconds();

        _databaseConnection.executeSql(
            new Query("UPDATE pending_blocks SET previous_block_hash = ?, timestamp = ? WHERE id = ?")
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

    protected void _deletePendingBlockData(final PendingBlockId pendingBlockId) throws DatabaseException {
        _databaseConnection.executeSql(
            new Query("DELETE FROM pending_block_data WHERE pending_block_id = ?")
                .setParameter(pendingBlockId)
        );
    }

    protected void _deletePendingBlock(final PendingBlockId pendingBlockId) throws DatabaseException {
        _deletePendingBlockData(pendingBlockId);

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
        try {
            READ_LOCK.lock();

            return _getPendingBlockId(blockHash);

        }
        finally {
            READ_LOCK.unlock();
        }
    }

    public Boolean hasBlockData(final PendingBlockId pendingBlockId) throws DatabaseException {
        try {
            READ_LOCK.lock();

            return _hasBlockData(pendingBlockId);

        }
        finally {
            READ_LOCK.unlock();
        }
    }

    public Boolean pendingBlockExists(final Sha256Hash blockHash) throws DatabaseException {
        try {
            READ_LOCK.lock();

            final PendingBlockId pendingBlockId = _getPendingBlockId(blockHash);
            return (pendingBlockId != null);

        }
        finally {
            READ_LOCK.unlock();
        }
    }

    public List<PendingBlockId> getPendingBlockIdsWithPreviousBlockHash(final Sha256Hash previousBlockHash) throws DatabaseException {
        try {
            READ_LOCK.lock();

            return _getPendingBlockIdsWithPreviousBlockHash(previousBlockHash);

        }
        finally {
            READ_LOCK.unlock();
        }
    }

    public PendingBlockId insertBlockHash(final Sha256Hash blockHash) throws DatabaseException {
        try {
            WRITE_LOCK.lock();

            return _storePendingBlock(blockHash, null);

        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public PendingBlockId storeBlockHash(final Sha256Hash blockHash) throws DatabaseException {
        try {
            WRITE_LOCK.lock();

            final PendingBlockId existingPendingBlockId = _getPendingBlockId(blockHash);
            if (existingPendingBlockId != null) { return existingPendingBlockId; }

            return _storePendingBlock(blockHash, null);

        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public PendingBlockId storeBlockHash(final Sha256Hash blockHash, final Sha256Hash previousBlockHash) throws DatabaseException {
        try {
            WRITE_LOCK.lock();

            final PendingBlockId existingPendingBlockId = _getPendingBlockId(blockHash);
            if (existingPendingBlockId != null) { return existingPendingBlockId; }

            return _storePendingBlock(blockHash, previousBlockHash);

        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public PendingBlockId storeBlock(final Block block) throws DatabaseException {
        try {
            WRITE_LOCK.lock();

            final Sha256Hash blockHash = block.getHash();
            final Sha256Hash previousBlockHash = block.getPreviousBlockHash();

            final PendingBlockId pendingBlockId;
            {
                final PendingBlockId existingPendingBlockId = _getPendingBlockId(blockHash);
                if (existingPendingBlockId != null) {
                    _updatePendingBlock(existingPendingBlockId, previousBlockHash);
                    pendingBlockId = existingPendingBlockId;
                }
                else {
                    pendingBlockId = _storePendingBlock(blockHash, previousBlockHash);
                }
            }

            final BlockDeflater blockDeflater = new BlockDeflater();
            _insertPendingBlockData(pendingBlockId, blockDeflater.toBytes(block));
            return pendingBlockId;

        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public List<PendingBlockId> selectIncompletePendingBlocks(final Integer maxCount) throws DatabaseException {
        try {
            READ_LOCK.lock();

            final Long minSecondsBetweenDownloadAttempts = 5L;
            final Long currentTimestamp = _systemTime.getCurrentTimeInSeconds();
            final java.util.List<Row> rows = _databaseConnection.query(
                new Query("SELECT pending_blocks.id FROM pending_blocks LEFT OUTER JOIN pending_block_data ON pending_blocks.id = pending_block_data.pending_block_id WHERE (pending_block_data.id IS NULL) AND ( (? - COALESCE(last_download_attempt_timestamp, 0)) > ? ) ORDER BY pending_blocks.priority ASC, pending_blocks.id ASC LIMIT " + Util.coalesce(maxCount, Integer.MAX_VALUE))
                    .setParameter(currentTimestamp)
                    .setParameter(minSecondsBetweenDownloadAttempts)
            );

            final ImmutableListBuilder<PendingBlockId> pendingBlockIdsBuilder = new ImmutableListBuilder<PendingBlockId>(rows.size());
            for (final Row row : rows) {
                pendingBlockIdsBuilder.add(PendingBlockId.wrap(row.getLong("id")));
            }
            return pendingBlockIdsBuilder.build();

        }
        finally {
            READ_LOCK.unlock();
        }
    }

    public PendingBlockId selectCandidatePendingBlockId() throws DatabaseException {
        try {
            READ_LOCK.lock();

            final java.util.List<Row> rows = _databaseConnection.query(
                new Query("SELECT pending_blocks.id FROM pending_blocks INNER JOIN pending_block_data ON pending_blocks.id = pending_block_data.pending_block_id INNER JOIN blocks ON blocks.hash = pending_blocks.previous_block_hash INNER JOIN block_transactions ON block_transactions.block_id = blocks.id GROUP BY block_transactions.block_id ORDER BY pending_blocks.priority ASC LIMIT 1")
            );
            if (rows.isEmpty()) { return null; }

            final Row row = rows.get(0);
            return PendingBlockId.wrap(row.getLong("id"));

        }
        finally {
            READ_LOCK.unlock();
        }
    }

    public Sha256Hash getPendingBlockHash(final PendingBlockId pendingBlockId) throws DatabaseException {
        try {
            READ_LOCK.lock();

            final java.util.List<Row> rows = _databaseConnection.query(
                new Query("SELECT id, hash FROM pending_blocks WHERE id = ?")
                    .setParameter(pendingBlockId)
            );
            if (rows.isEmpty()) { return null; }

            final Row row = rows.get(0);
            return Sha256Hash.fromHexString(row.getString("hash"));

        }
        finally {
            READ_LOCK.unlock();
        }
    }

    public void incrementFailedDownloadCount(final PendingBlockId pendingBlockId) throws DatabaseException {
        try {
            WRITE_LOCK.lock();

            _databaseConnection.executeSql(
                new Query("UPDATE pending_blocks SET failed_download_count = failed_download_count + 1, priority = priority + 60 WHERE id = ?")
                    .setParameter(pendingBlockId)
            );

        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public void updateLastDownloadAttemptTime(final PendingBlockId pendingBlockId) throws DatabaseException {
        try {
            WRITE_LOCK.lock();

            final Long currentTimestamp = _systemTime.getCurrentTimeInSeconds();
            _databaseConnection.executeSql(
                new Query("UPDATE pending_blocks SET last_download_attempt_timestamp = ? WHERE id = ?")
                    .setParameter(currentTimestamp)
                    .setParameter(pendingBlockId)
            );

        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public void setPriority(final PendingBlockId pendingBlockId, final Long priority) throws DatabaseException {
        try {
            WRITE_LOCK.lock();

            _databaseConnection.executeSql(
                new Query("UPDATE pending_blocks SET priority = ? WHERE id = ?")
                    .setParameter(priority)
                    .setParameter(pendingBlockId)
            );

        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public void purgeFailedPendingBlocks(final Integer maxFailedDownloadCount) throws DatabaseException {
        try {
            WRITE_LOCK.lock();

            final java.util.List<Row> rows = _databaseConnection.query(
                new Query("SELECT pending_blocks.id FROM pending_blocks LEFT OUTER JOIN pending_block_data ON (pending_blocks.id = pending_block_data.pending_block_id) WHERE pending_blocks.failed_download_count > ? AND pending_block_data.id IS NULL")
                    .setParameter(maxFailedDownloadCount)
            );

            for (final Row row : rows) {
                final PendingBlockId pendingBlockId = PendingBlockId.wrap(row.getLong("id"));
                Logger.log("Deleting Failed Pending Block: " + pendingBlockId);
                _deletePendingBlock(pendingBlockId);
            }

        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public PendingBlock getPendingBlock(final PendingBlockId pendingBlockId) throws DatabaseException {
        try {
            READ_LOCK.lock();

            return _getPendingBlock(pendingBlockId, true);

        }
        finally {
            READ_LOCK.unlock();
        }
    }

    public void deletePendingBlock(final PendingBlockId pendingBlockId) throws DatabaseException {
        try {
            WRITE_LOCK.lock();

            _deletePendingBlock(pendingBlockId);

        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public void deletePendingBlockData(final PendingBlockId pendingBlockId) throws DatabaseException {
        try {
            WRITE_LOCK.lock();

            _deletePendingBlockData(pendingBlockId);

        }
        finally {
            WRITE_LOCK.unlock();
        }
    }
}
