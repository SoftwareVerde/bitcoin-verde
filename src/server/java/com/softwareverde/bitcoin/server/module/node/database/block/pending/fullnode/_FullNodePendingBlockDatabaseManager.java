package com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.database.query.ValueExtractor;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStore;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending._PendingBlock;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class _FullNodePendingBlockDatabaseManager {
    protected final SystemTime _systemTime = new SystemTime();
    protected final FullNodeDatabaseManager _databaseManager;
    protected final PendingBlockStore _blockStore;

    protected Sha256Hash _getPendingBlockHash(final PendingBlockId pendingBlockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, hash FROM pending_blocks WHERE id = ?")
                .setParameter(pendingBlockId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return Sha256Hash.wrap(row.getBytes("hash"));
    }

    protected PendingBlockId _getPendingBlockId(final Sha256Hash blockHash) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM pending_blocks WHERE hash = ?")
                .setParameter(blockHash)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return PendingBlockId.wrap(row.getLong("id"));
    }

    protected List<PendingBlockId> _getPendingBlockIdsWithPreviousBlockHash(final Sha256Hash blockHash) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
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

    protected PendingBlockId _storePendingBlock(final Sha256Hash blockHash, final Sha256Hash previousBlockHash, final Boolean wasDownloaded) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

        final Long currentTimestamp = _systemTime.getCurrentTimeInSeconds();
        final Long priority;
        {
            final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
            if (blockId != null) {
                priority = blockHeaderDatabaseManager.getBlockTimestamp(blockId);
            }
            else {
                priority = currentTimestamp;
            }
        }

        final Long pendingBlockId = databaseConnection.executeSql(
            new Query("INSERT INTO pending_blocks (hash, previous_block_hash, timestamp, priority, was_downloaded) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE priority = VALUES(priority), was_downloaded = VALUES(was_downloaded)")
                .setParameter(blockHash)
                .setParameter(previousBlockHash)
                .setParameter(currentTimestamp)
                .setParameter(priority)
                .setParameter(wasDownloaded ? 1 : 0)
        );

        if (pendingBlockId < 1) { // -1 may be returned if no insert occurred.
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
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Long currentTimestamp = _systemTime.getCurrentTimeInSeconds();

        databaseConnection.executeSql(
            new Query("UPDATE pending_blocks SET previous_block_hash = ?, timestamp = ? WHERE id = ?")
                .setParameter(previousBlockHash)
                .setParameter(currentTimestamp)
                .setParameter(pendingBlockId)
        );
    }

    protected void _insertPendingBlockData(final PendingBlockId pendingBlockId, final Block pendingBlock) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Boolean storeWasSuccessful = _blockStore.storePendingBlock(pendingBlock);
        if (! storeWasSuccessful) {
            throw new DatabaseException("Error storing pending block: " + pendingBlock.getHash());
        }

        Logger.trace("Stored data for pending block: " + pendingBlock.getHash());
        databaseConnection.executeSql(
            new Query("UPDATE pending_blocks SET was_downloaded = 1 WHERE id = ?")
                .setParameter(pendingBlockId)
        );
    }

    protected void _deletePendingBlock(final PendingBlockId pendingBlockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        // Load the block hash before the record is deleted...
        final Sha256Hash blockHash = _getPendingBlockHash(pendingBlockId);

        databaseConnection.executeSql(
            new Query("DELETE FROM pending_blocks WHERE id = ?")
                .setParameter(pendingBlockId)
        );

        // Remove the block data only after the query succeeds...
        if (blockHash != null) {
            _blockStore.removePendingBlock(blockHash);
        }
    }

    protected void _deletePendingBlocks(final List<PendingBlockId> pendingBlockIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        if (pendingBlockIds.isEmpty()) { return; }

        // Load the block hashes before the records are deleted...
        final HashSet<Sha256Hash> pendingBlockHashes = new HashSet<Sha256Hash>(pendingBlockIds.getCount());
        for (final PendingBlockId pendingBlockId : pendingBlockIds) {
            final Sha256Hash blockHash = _getPendingBlockHash(pendingBlockId);
            if (blockHash != null) {
                pendingBlockHashes.add(blockHash);
            }
        }

        databaseConnection.executeSql(
            new Query("DELETE FROM pending_blocks WHERE id IN (?)")
                .setInClauseParameters(pendingBlockIds, ValueExtractor.IDENTIFIER)
        );

        // Remove the block data only after the query succeeds...
        for (final Sha256Hash blockHash : pendingBlockHashes) {
            if (blockHash != null) {
                _blockStore.removePendingBlock(blockHash);
            }
        }
    }

    protected Boolean _hasBlockData(final PendingBlockId pendingBlockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, hash, was_downloaded FROM pending_blocks WHERE id = ?")
                .setParameter(pendingBlockId)
        );
        if (rows.isEmpty()) { return false; }

        final Row row = rows.get(0);
        final Sha256Hash blockHash = Sha256Hash.wrap(row.getBytes("hash"));
        final Boolean wasDownloaded = row.getBoolean("was_downloaded");
        if (! wasDownloaded) { return false; }

        return _blockStore.pendingBlockExists(blockHash);
    }

    protected ByteArray _getBlockData(final PendingBlockId pendingBlockId) throws DatabaseException {
        return _getPendingBlockHash(pendingBlockId);
    }

    protected _PendingBlock _getPendingBlock(final PendingBlockId pendingBlockId, final Boolean includeDataIfAvailable) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, hash, previous_block_hash FROM pending_blocks WHERE id = ?")
                .setParameter(pendingBlockId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Sha256Hash blockHash = Sha256Hash.wrap(row.getBytes("hash"));

        final byte[] previousBlockHashBytes = row.getBytes("previous_block_hash");
        final Sha256Hash previousBlockHash = Sha256Hash.wrap(previousBlockHashBytes);

        final ByteArray blockData;
        {
            if (includeDataIfAvailable) {
                blockData = _blockStore.getPendingBlockData(blockHash);
            }
            else {
                blockData = null;
            }
        }

        return new _PendingBlock(blockHash, previousBlockHash, blockData);
    }

    public _FullNodePendingBlockDatabaseManager(final FullNodeDatabaseManager databaseManager, final PendingBlockStore blockStore) {
        _databaseManager = databaseManager;
        _blockStore = blockStore;
    }

    public PendingBlockId getPendingBlockId(final Sha256Hash blockHash) throws DatabaseException {
        return _getPendingBlockId(blockHash);
    }

    public Boolean hasBlockData(final PendingBlockId pendingBlockId) throws DatabaseException {
        return _hasBlockData(pendingBlockId);
    }

    /**
     * Returns true if the blockHash is definitively connected to the current head blockchain.
     *  Returns null if the relationship could not be determined.
     */
    public Boolean isPendingBlockConnectedToMainChain(final Sha256Hash blockHash) throws DatabaseException {
        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
        if (blockId == null) { return null; }

        final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
        if (headBlockchainSegmentId == null) { return null; }

        final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);
        return blockchainDatabaseManager.areBlockchainSegmentsConnected(blockchainSegmentId, headBlockchainSegmentId, BlockRelationship.ANY);
    }

    public Boolean pendingBlockExists(final Sha256Hash blockHash) throws DatabaseException {
        final PendingBlockId pendingBlockId = _getPendingBlockId(blockHash);
        return (pendingBlockId != null);
    }

    public List<PendingBlockId> getPendingBlockIdsWithPreviousBlockHash(final Sha256Hash previousBlockHash) throws DatabaseException {
        return _getPendingBlockIdsWithPreviousBlockHash(previousBlockHash);
    }

    public PendingBlockId storeBlockHash(final Sha256Hash blockHash, final Sha256Hash previousBlockHash, final Boolean wasDownloaded) throws DatabaseException {
        return _storePendingBlock(blockHash, previousBlockHash, wasDownloaded);
    }

    /**
     * Inserts the blockHash into PendingBlocks if it does not exist.
     *  If previousBlockHash is provided, then the PendingBlock is updated to include the previousBlockHash.
     */
    public PendingBlockId storeBlockHash(final Sha256Hash blockHash, final Sha256Hash previousBlockHash) throws DatabaseException {
        DatabaseException deadlockException = null;
        final PendingBlockId existingPendingBlockId = _getPendingBlockId(blockHash);

        if (existingPendingBlockId != null) {
            for (int i = 0; i < 3; ++i) {
                try {
                    if (previousBlockHash != null) {
                        _updatePendingBlock(existingPendingBlockId, previousBlockHash);
                    }
                    return existingPendingBlockId;
                }
                catch (final DatabaseException exception) {
                    deadlockException = exception;
                }
            }
            throw deadlockException;
        }

        for (int i = 0; i < 3; ++i) {
            try {
                return _storePendingBlock(blockHash, previousBlockHash, false);
            }
            catch (final DatabaseException exception) {
                deadlockException = exception;
            }
        }
        throw deadlockException;
    }

    public PendingBlockId storeBlock(final Block block) throws DatabaseException {
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
                pendingBlockId = _storePendingBlock(blockHash, previousBlockHash, false);
            }
        }

        _insertPendingBlockData(pendingBlockId, block);
        return pendingBlockId;
    }

    public static class DownloadPlan {
        protected final MutableList<PendingBlockId> _pendingBlockIds = new MutableList<>();
        protected final Map<PendingBlockId, Long> _blockHeights = new HashMap<>();

        protected void addPendingBlock(final PendingBlockId pendingBlockId, final Long blockHeight) {
            _pendingBlockIds.add(pendingBlockId);
            _blockHeights.put(pendingBlockId, blockHeight);
        }

        public DownloadPlan() { }

        public Long getBlockHeight(final PendingBlockId pendingBlockId) {
            return _blockHeights.get(pendingBlockId);
        }

        public List<PendingBlockId> getPendingBlockIds() {
            return _pendingBlockIds;
        }

        public Integer getCount() {
            return _pendingBlockIds.getCount();
        }

        public Boolean isEmpty() {
            return _pendingBlockIds.isEmpty();
        }

        public Long getMinimumBlockHeight() {
            Long minBlockHeight = null;
            for (final Long incompletePendingBlocksBlockHeight : _blockHeights.values()) {
                if (incompletePendingBlocksBlockHeight == null) { continue; }

                if (minBlockHeight == null) {
                    minBlockHeight = incompletePendingBlocksBlockHeight;
                }
                else if (incompletePendingBlocksBlockHeight < minBlockHeight) {
                    minBlockHeight = incompletePendingBlocksBlockHeight;
                }
            }
            return minBlockHeight;
        }
    }

    public DownloadPlan selectIncompletePendingBlocks(final Integer maxBlockCount) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Long minSecondsBetweenDownloadAttempts = 5L;
        final Long currentTimestamp = _systemTime.getCurrentTimeInSeconds();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT pending_blocks.id, pending_blocks.hash, blocks.block_height FROM pending_blocks LEFT OUTER JOIN blocks ON blocks.hash = pending_blocks.hash WHERE was_downloaded = 0 AND (? - COALESCE(last_download_attempt_timestamp, 0)) > ? ORDER BY priority ASC, id ASC LIMIT " + Util.coalesce(maxBlockCount, Integer.MAX_VALUE))
                .setParameter(currentTimestamp)
                .setParameter(minSecondsBetweenDownloadAttempts)
        );

        final DownloadPlan downloadPlan = new DownloadPlan();
        for (final Row row : rows) {
            final PendingBlockId pendingBlockId = PendingBlockId.wrap(row.getLong("id"));
            final Long blockHeight = row.getLong("block_height");
            downloadPlan.addPendingBlock(pendingBlockId, blockHeight);
        }
        return downloadPlan;
    }

    public Sha256Hash getPendingBlockHash(final PendingBlockId pendingBlockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, hash FROM pending_blocks WHERE id = ?")
                .setParameter(pendingBlockId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return Sha256Hash.wrap(row.getBytes("hash"));
    }

    public void incrementFailedDownloadCount(final PendingBlockId pendingBlockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        databaseConnection.executeSql(
            new Query("UPDATE pending_blocks SET failed_download_count = failed_download_count + 1, priority = priority + 60 WHERE id = ?")
                .setParameter(pendingBlockId)
        );
    }

    public void updateLastDownloadAttemptTime(final PendingBlockId pendingBlockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Long currentTimestamp = _systemTime.getCurrentTimeInSeconds();
        databaseConnection.executeSql(
            new Query("UPDATE pending_blocks SET last_download_attempt_timestamp = ? WHERE id = ?")
                .setParameter(currentTimestamp)
                .setParameter(pendingBlockId)
        );
    }

    public void setPriority(final PendingBlockId pendingBlockId, final Long priority) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        databaseConnection.executeSql(
            new Query("UPDATE pending_blocks SET priority = ? WHERE id = ?")
                .setParameter(priority)
                .setParameter(pendingBlockId)
        );
    }

    public void purgeFailedPendingBlocks(final Integer maxFailedDownloadCount) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT pending_blocks.id FROM pending_blocks WHERE pending_blocks.failed_download_count > ? AND pending_blocks.was_downloaded = 0")
                .setParameter(maxFailedDownloadCount)
        );

        final MutableList<PendingBlockId> pendingBlockIds = new MutableList<PendingBlockId>(rows.size());
        for (final Row row : rows) {
            final PendingBlockId pendingBlockId = PendingBlockId.wrap(row.getLong("id"));
            Logger.debug("Deleting Failed Pending Block: " + pendingBlockId);
            pendingBlockIds.add(pendingBlockId);
        }

        _deletePendingBlocks(pendingBlockIds);
    }

    public _PendingBlock getPendingBlock(final PendingBlockId pendingBlockId) throws DatabaseException {
        return _getPendingBlock(pendingBlockId, true);
    }

    public void deletePendingBlock(final PendingBlockId pendingBlockId) throws DatabaseException {
        _deletePendingBlock(pendingBlockId);
    }

    public void deletePendingBlocks(final List<PendingBlockId> pendingBlockIds) throws DatabaseException {
        _deletePendingBlocks(pendingBlockIds);
    }

    /**
     * Deletes any pending blocks that have been completely processed.
     *  This can happen (rarely) due to a race condition between the InventoryHandler and the BlockchainBuilder...
     *  Since duplicates are fairly innocuous, it is better to run a cleanup than to introduce contention between the two components.
     */
    public void cleanupPendingBlocks() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            // NOTE: Selecting the rows first is more performant than doing the join/delete as a single query since the select does not require a lock on the blocks table...
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT pending_blocks.id FROM pending_blocks INNER JOIN blocks ON blocks.hash = pending_blocks.hash WHERE blocks.has_transactions = 1")
            );

            for (final Row row : rows) {
                final PendingBlockId pendingBlockId = PendingBlockId.wrap(row.getLong("id"));
                databaseConnection.executeSql(
                    new Query("DELETE FROM pending_blocks WHERE id = ?")
                        .setParameter(pendingBlockId)
                );
            }
        }
    }

    public List<BlockchainSegmentId> getLeafBlockchainSegmentsByChainWork() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT blockchain_segments.id FROM blockchain_segments INNER JOIN (SELECT blockchain_segment_id, MAX(chain_work) AS chain_work FROM blocks GROUP BY blockchain_segment_id) AS segment_head_block WHERE nested_set_right = nested_set_left + 1 AND segment_head_block.blockchain_segment_id = blockchain_segments.id ORDER BY segment_head_block.chain_work DESC")
        );

        final MutableList<BlockchainSegmentId> orderedSegments = new MutableList<>();
        for (final Row row : rows) {
            final BlockchainSegmentId blockchainSegmentId = BlockchainSegmentId.wrap(row.getLong("id"));
            orderedSegments.add(blockchainSegmentId);
        }
        return orderedSegments;
    }
}
