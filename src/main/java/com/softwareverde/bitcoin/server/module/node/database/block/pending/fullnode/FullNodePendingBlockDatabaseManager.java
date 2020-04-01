package com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.database.query.ValueExtractor;
import com.softwareverde.bitcoin.server.module.node.PendingBlockStore;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.PendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlock;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

import java.util.HashMap;
import java.util.Map;

public class FullNodePendingBlockDatabaseManager implements PendingBlockDatabaseManager {
    protected final SystemTime _systemTime = new SystemTime();
    protected final DatabaseManager _databaseManager;
    protected final PendingBlockStore _blockStore;

    protected Sha256Hash _getPendingBlockHash(final PendingBlockId pendingBlockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, hash FROM pending_blocks WHERE id = ?")
                .setParameter(pendingBlockId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return Sha256Hash.copyOf(row.getBytes("hash"));
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

    protected PendingBlockId _storePendingBlock(final Sha256Hash blockHash, final Sha256Hash previousBlockHash) throws DatabaseException {
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
            new Query("INSERT IGNORE INTO pending_blocks (hash, previous_block_hash, timestamp, priority) VALUES (?, ?, ?, ?)")
                .setParameter(blockHash)
                .setParameter(previousBlockHash)
                .setParameter(currentTimestamp)
                .setParameter(priority)
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
        final MutableList<Sha256Hash> pendingBlockHashes = new MutableList<Sha256Hash>(pendingBlockIds.getCount());
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
        final Sha256Hash blockHash = Sha256Hash.copyOf(row.getBytes("hash"));
        final Boolean wasDownloaded = row.getBoolean("was_downloaded");
        if (! wasDownloaded) { return false; }

        return _blockStore.pendingBlockExists(blockHash);
    }

    protected ByteArray _getBlockData(final PendingBlockId pendingBlockId) throws DatabaseException {
        return _getPendingBlockHash(pendingBlockId);
    }

    protected PendingBlock _getPendingBlock(final PendingBlockId pendingBlockId, final Boolean includeDataIfAvailable) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, hash, previous_block_hash FROM pending_blocks WHERE id = ?")
                .setParameter(pendingBlockId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Sha256Hash blockHash = Sha256Hash.copyOf(row.getBytes("hash"));
        final Sha256Hash previousBlockHash = Sha256Hash.copyOf(row.getBytes("previous_block_hash"));

        final ByteArray blockData;
        {
            if (includeDataIfAvailable) {
                blockData = _blockStore.getPendingBlockData(blockHash);
            }
            else {
                blockData = null;
            }
        }

        return new PendingBlock(blockHash, previousBlockHash, blockData);
    }

    public FullNodePendingBlockDatabaseManager(final DatabaseManager databaseManager, final PendingBlockStore blockStore) {
        _databaseManager = databaseManager;
        _blockStore = blockStore;
    }

    public PendingBlockId getPendingBlockId(final Sha256Hash blockHash) throws DatabaseException {
        try {
            READ_WRITE_LOCK.lock();

            return _getPendingBlockId(blockHash);

        }
        finally {
            READ_WRITE_LOCK.unlock();
        }
    }

    public Boolean hasBlockData(final PendingBlockId pendingBlockId) throws DatabaseException {
        try {
            READ_WRITE_LOCK.lock();

            return _hasBlockData(pendingBlockId);

        }
        finally {
            READ_WRITE_LOCK.unlock();
        }
    }

    public Boolean pendingBlockExists(final Sha256Hash blockHash) throws DatabaseException {
        try {
            READ_WRITE_LOCK.lock();

            final PendingBlockId pendingBlockId = _getPendingBlockId(blockHash);
            return (pendingBlockId != null);

        }
        finally {
            READ_WRITE_LOCK.unlock();
        }
    }

    public List<PendingBlockId> getPendingBlockIdsWithPreviousBlockHash(final Sha256Hash previousBlockHash) throws DatabaseException {
        try {
            READ_WRITE_LOCK.lock();

            return _getPendingBlockIdsWithPreviousBlockHash(previousBlockHash);

        }
        finally {
            READ_WRITE_LOCK.unlock();
        }
    }

    public PendingBlockId insertBlockHash(final Sha256Hash blockHash) throws DatabaseException {
        try {
            READ_WRITE_LOCK.lock();

            return _storePendingBlock(blockHash, null);

        }
        finally {
            READ_WRITE_LOCK.unlock();
        }
    }

    /**
     * Inserts the blockHash into PendingBlocks if it does not exist.
     *  If previousBlockHash is provided, then the PendingBlock is updated to include the previousBlockHash.
     */
    public PendingBlockId storeBlockHash(final Sha256Hash blockHash) throws DatabaseException {
        try {
            READ_WRITE_LOCK.lock();

            final PendingBlockId existingPendingBlockId = _getPendingBlockId(blockHash);
            if (existingPendingBlockId != null) { return existingPendingBlockId; }

            return _storePendingBlock(blockHash, null);

        }
        finally {
            READ_WRITE_LOCK.unlock();
        }
    }

    /**
     * Inserts the blockHash into PendingBlocks if it does not exist.
     *  If previousBlockHash is provided, then the PendingBlock is updated to include the previousBlockHash.
     */
    public PendingBlockId storeBlockHash(final Sha256Hash blockHash, final Sha256Hash previousBlockHash) throws DatabaseException {
        try {
            READ_WRITE_LOCK.lock();

            final PendingBlockId existingPendingBlockId = _getPendingBlockId(blockHash);
            if (existingPendingBlockId != null) { return existingPendingBlockId; }

            DatabaseException deadlockException = null;
            for (int i = 0; i < 3; ++i) {
                try {
                    return _storePendingBlock(blockHash, previousBlockHash);
                }
                catch (final DatabaseException exception) {
                    deadlockException = exception;
                }
            }
            throw deadlockException;

        }
        finally {
            READ_WRITE_LOCK.unlock();
        }
    }

    public PendingBlockId storeBlock(final Block block) throws DatabaseException {
        try {
            READ_WRITE_LOCK.lock();

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

            _insertPendingBlockData(pendingBlockId, block);
            return pendingBlockId;
        }
        finally {
            READ_WRITE_LOCK.unlock();
        }
    }

    @Override
    public List<Tuple<Sha256Hash, Sha256Hash>> selectPriorityPendingBlocksWithUnknownNodeInventory(final List<NodeId> connectedNodeIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        try {
            READ_WRITE_LOCK.lock();

            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT blocks.block_height, pending_blocks.hash FROM pending_blocks LEFT OUTER JOIN node_blocks_inventory ON node_blocks_inventory.pending_block_id = pending_blocks.id AND node_blocks_inventory.node_id IN (?) LEFT OUTER JOIN blocks ON blocks.hash = pending_blocks.hash WHERE (pending_blocks.was_downloaded = 0) AND (node_blocks_inventory.id IS NULL) ORDER BY pending_blocks.priority ASC, pending_blocks.id ASC LIMIT 500")
                    .setInClauseParameters(connectedNodeIds, ValueExtractor.IDENTIFIER)
            );

            final MutableList<Tuple<Sha256Hash, Sha256Hash>> downloadPlan = new MutableList<Tuple<Sha256Hash, Sha256Hash>>(rows.size());

            Tuple<Sha256Hash, Sha256Hash> blockHashStartEnd = null;
            Long tupleStartingBlockHeight = null; // The blockHeight of blockHashStartEnd.first...
            for (final Row row : rows) {
                final Long blockHeight = row.getLong("block_height");
                final Sha256Hash blockHash = Sha256Hash.copyOf(row.getBytes("hash"));

                boolean addTupleToDownloadPlan = false;
                boolean createNewTuple = false;
                if (blockHashStartEnd == null) {
                    createNewTuple = true;

                    if (blockHeight == null) {
                        addTupleToDownloadPlan = true;
                    }
                }
                else {
                    if (blockHeight == null) {
                        addTupleToDownloadPlan = true;
                        createNewTuple = true;
                    }
                    else {
                        if (tupleStartingBlockHeight == null) {
                            createNewTuple = true;
                            addTupleToDownloadPlan = true;
                        }
                        else {
                            final long blockHeightDifference = (blockHeight - tupleStartingBlockHeight);
                            if ((blockHeightDifference < 0) || (blockHeightDifference >= 500)) {
                                addTupleToDownloadPlan = true;
                            }
                            else {
                                blockHashStartEnd.second = blockHash;
                            }
                        }
                    }
                }

                if (addTupleToDownloadPlan) {
                    if (blockHashStartEnd != null) {
                        downloadPlan.add(blockHashStartEnd);
                        blockHashStartEnd = null;
                        tupleStartingBlockHeight = null;
                    }
                }

                if (createNewTuple) {
                    blockHashStartEnd = new Tuple<Sha256Hash, Sha256Hash>();
                    blockHashStartEnd.first = blockHash;
                    tupleStartingBlockHeight = blockHeight;
                }
            }
            if (blockHashStartEnd != null) {
                downloadPlan.add(blockHashStartEnd);
            }

            return downloadPlan;

        }
        finally {
            READ_WRITE_LOCK.unlock();
        }
    }

    public Boolean nodesHaveBlockInventory(final List<NodeId> connectedNodeIds, final Sha256Hash blockHash) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        try {
            READ_WRITE_LOCK.lock();

            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT pending_blocks.id FROM pending_blocks INNER JOIN node_blocks_inventory ON node_blocks_inventory.pending_block_id = pending_blocks.id WHERE (pending_blocks.hash = ?) AND (node_blocks_inventory.node_id IN (?)) LIMIT 1")
                    .setParameter(blockHash)
                    .setInClauseParameters(connectedNodeIds, ValueExtractor.IDENTIFIER)
            );

            return (! rows.isEmpty());

        }
        finally {
            READ_WRITE_LOCK.unlock();
        }
    }

    public Map<PendingBlockId, NodeId> selectIncompletePendingBlocks(final List<NodeId> connectedNodeIds, final Integer maxBlockCount) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        try {
            READ_WRITE_LOCK.lock();

            final Long minSecondsBetweenDownloadAttempts = 5L;
            final Long currentTimestamp = _systemTime.getCurrentTimeInSeconds();

            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT node_blocks_inventory.node_id, pending_blocks.id AS pending_block_id FROM pending_blocks INNER JOIN node_blocks_inventory ON node_blocks_inventory.pending_block_id = pending_blocks.id WHERE (pending_blocks.was_downloaded = 0) AND ( (? - COALESCE(last_download_attempt_timestamp, 0)) > ? ) AND (node_blocks_inventory.node_id IN (?)) ORDER BY pending_blocks.priority ASC, pending_blocks.id ASC LIMIT " + Util.coalesce(maxBlockCount, Integer.MAX_VALUE))
                    .setParameter(currentTimestamp)
                    .setParameter(minSecondsBetweenDownloadAttempts)
                    .setInClauseParameters(connectedNodeIds, ValueExtractor.IDENTIFIER)
            );

            final HashMap<PendingBlockId, NodeId> downloadPlan = new HashMap<PendingBlockId, NodeId>(rows.size());
            for (final Row row : rows) {
                final NodeId nodeId = NodeId.wrap(row.getLong("node_id"));
                final PendingBlockId pendingBlockId = PendingBlockId.wrap(row.getLong("pending_block_id"));
                downloadPlan.put(pendingBlockId, nodeId);
            }
            return downloadPlan;

        }
        finally {
            READ_WRITE_LOCK.unlock();
        }
    }

    public PendingBlockId selectCandidatePendingBlockId() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        try {
            READ_WRITE_LOCK.lock();

            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT pending_blocks.id FROM pending_blocks INNER JOIN blocks ON blocks.hash = pending_blocks.previous_block_hash INNER JOIN block_transactions ON block_transactions.block_id = blocks.id WHERE pending_blocks.was_downloaded = 1  GROUP BY block_transactions.block_id ORDER BY pending_blocks.priority ASC LIMIT 128")
            );

            for (final Row row : rows) {
                final PendingBlockId pendingBlockId = PendingBlockId.wrap(row.getLong("id"));
                if (_hasBlockData(pendingBlockId)) {
                    return pendingBlockId;
                }
            }

            return null;

        }
        finally {
            READ_WRITE_LOCK.unlock();
        }
    }

    public Sha256Hash getPendingBlockHash(final PendingBlockId pendingBlockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        try {
            READ_WRITE_LOCK.lock();

            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT id, hash FROM pending_blocks WHERE id = ?")
                    .setParameter(pendingBlockId)
            );
            if (rows.isEmpty()) { return null; }

            final Row row = rows.get(0);
            return Sha256Hash.copyOf(row.getBytes("hash"));

        }
        finally {
            READ_WRITE_LOCK.unlock();
        }
    }

    public void incrementFailedDownloadCount(final PendingBlockId pendingBlockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        try {
            READ_WRITE_LOCK.lock();

            databaseConnection.executeSql(
                new Query("UPDATE pending_blocks SET failed_download_count = failed_download_count + 1, priority = priority + 60 WHERE id = ?")
                    .setParameter(pendingBlockId)
            );

        }
        finally {
            READ_WRITE_LOCK.unlock();
        }
    }

    public void updateLastDownloadAttemptTime(final PendingBlockId pendingBlockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        try {
            READ_WRITE_LOCK.lock();

            final Long currentTimestamp = _systemTime.getCurrentTimeInSeconds();
            databaseConnection.executeSql(
                new Query("UPDATE pending_blocks SET last_download_attempt_timestamp = ? WHERE id = ?")
                    .setParameter(currentTimestamp)
                    .setParameter(pendingBlockId)
            );

        }
        finally {
            READ_WRITE_LOCK.unlock();
        }
    }

    public void setPriority(final PendingBlockId pendingBlockId, final Long priority) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        try {
            READ_WRITE_LOCK.lock();

            databaseConnection.executeSql(
                new Query("UPDATE pending_blocks SET priority = ? WHERE id = ?")
                    .setParameter(priority)
                    .setParameter(pendingBlockId)
            );

        }
        finally {
            READ_WRITE_LOCK.unlock();
        }
    }

    public void purgeFailedPendingBlocks(final Integer maxFailedDownloadCount) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        try {
            DELETE_LOCK.lock();

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
        finally {
            DELETE_LOCK.unlock();
        }
    }

    /**
     * Deletes any pending blocks that haven't been downloaded and do not have a peer to download them from.
     *  This can happen when a peer broadcasting block inventory disconnects and there are no other peers aware of their chain.
     */
    public void purgeUnlocatablePendingBlocks(final List<NodeId> connectedNodeIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        try {
            DELETE_LOCK.lock();

            final java.util.List<Row> rows = databaseConnection.query(
                // "Delete any pending_blocks that have not already been downloaded and do not have a connected node to download from..."
                new Query("SELECT pending_blocks.id FROM pending_blocks LEFT OUTER JOIN node_blocks_inventory ON (node_blocks_inventory.pending_block_id = pending_blocks.id AND node_blocks_inventory.node_id IN (?)) WHERE node_blocks_inventory.id IS NULL AND pending_blocks.was_downloaded = 0")
                    .setInClauseParameters(connectedNodeIds, ValueExtractor.IDENTIFIER)
            );

            final MutableList<PendingBlockId> pendingBlockIds = new MutableList<PendingBlockId>(rows.size());
            for (final Row row : rows) {
                final PendingBlockId pendingBlockId = PendingBlockId.wrap(row.getLong("id"));
                Logger.debug("Deleting Unlocatable Pending Block: " + pendingBlockId);
                pendingBlockIds.add(pendingBlockId);
            }

            _deletePendingBlocks(pendingBlockIds);

        }
        finally {
            DELETE_LOCK.unlock();
        }
    }

    public PendingBlock getPendingBlock(final PendingBlockId pendingBlockId) throws DatabaseException {
        try {
            READ_WRITE_LOCK.lock();

            return _getPendingBlock(pendingBlockId, true);

        }
        finally {
            READ_WRITE_LOCK.unlock();
        }
    }

    public void deletePendingBlock(final PendingBlockId pendingBlockId) throws DatabaseException {
        try {
            DELETE_LOCK.lock();

            _deletePendingBlock(pendingBlockId);

        }
        finally {
            DELETE_LOCK.unlock();
        }
    }

    /**
     * Deletes any pending blocks that have been completely processed.
     *  This can happen (rarely) due to a race condition between the InventoryHandler and the BlockchainBuilder...
     *  Since duplicates are fairly innocuous, it is better to run a cleanup than to introduce contention between the two components.
     */
    public void cleanupPendingBlocks() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        try {
            DELETE_LOCK.lock();

            databaseConnection.executeSql(
                new Query("DELETE pending_blocks FROM pending_blocks INNER JOIN blocks ON blocks.hash = pending_blocks.hash WHERE blocks.transaction_count > 0")
            );

        }
        finally {
            DELETE_LOCK.unlock();
        }
    }
}