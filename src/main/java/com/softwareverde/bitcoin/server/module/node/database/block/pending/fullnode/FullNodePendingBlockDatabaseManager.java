package com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.database.query.ValueExtractor;
import com.softwareverde.bitcoin.server.message.type.query.block.QueryBlocksMessage;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.PendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.inventory.MutableUnknownBlockInventory;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.inventory.UnknownBlockInventory;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStore;
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
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

import java.util.HashMap;
import java.util.HashSet;
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
            new Query("INSERT IGNORE INTO pending_blocks (hash, previous_block_hash, timestamp, priority, was_downloaded) VALUES (?, ?, ?, ?, ?)")
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

        databaseConnection.executeSql(
            new Query("DELETE FROM node_blocks_inventory WHERE hash = ?")
                .setParameter(blockHash)
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

        databaseConnection.executeSql(
            new Query("DELETE FROM node_blocks_inventory WHERE hash IN (?)")
                .setInClauseParameters(pendingBlockHashes, ValueExtractor.SHA256_HASH)
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

    protected PendingBlock _getPendingBlock(final PendingBlockId pendingBlockId, final Boolean includeDataIfAvailable) throws DatabaseException {
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

        return new PendingBlock(blockHash, previousBlockHash, blockData);
    }

    public FullNodePendingBlockDatabaseManager(final DatabaseManager databaseManager, final PendingBlockStore blockStore) {
        _databaseManager = databaseManager;
        _blockStore = blockStore;
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

    public PendingBlockId insertBlockHash(final Sha256Hash blockHash, final Sha256Hash previousBlockHash, final Boolean wasDownloaded) throws DatabaseException {
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

    /**
     * Returns a List of UnknownInventory objects to be used within a set of BlockFinder/BlockHeader requests.
     *  The previousBlockHash may be null if it is unknown.
     */
    @Override
    public List<UnknownBlockInventory> findUnknownNodeInventoryByPriority(final List<NodeId> connectedNodeIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT blocks.block_height, pending_blocks.hash, pending_blocks.previous_block_hash FROM pending_blocks LEFT OUTER JOIN node_blocks_inventory ON node_blocks_inventory.hash = pending_blocks.hash AND node_blocks_inventory.node_id IN (?) LEFT OUTER JOIN blocks ON blocks.hash = pending_blocks.hash WHERE (pending_blocks.was_downloaded = 0) AND (node_blocks_inventory.id IS NULL) ORDER BY pending_blocks.priority ASC, pending_blocks.id ASC LIMIT 500")
                .setInClauseParameters(connectedNodeIds, ValueExtractor.IDENTIFIER)
        );

        final MutableList<UnknownBlockInventory> downloadPlan = new MutableList<UnknownBlockInventory>(rows.size());

        MutableUnknownBlockInventory mutableUnknownBlockInventory = null;
        Long tupleStartingBlockHeight = null; // The blockHeight of mutableUnknownBlockInventory.first...
        for (final Row row : rows) {
            final Long blockHeight = row.getLong("block_height");
            final Sha256Hash blockHash = Sha256Hash.wrap(row.getBytes("hash"));
            final Sha256Hash previousBlockHash = Sha256Hash.wrap(row.getBytes("previous_block_hash"));

            boolean addTupleToDownloadPlan = false;
            boolean createNewTuple = false;
            if (mutableUnknownBlockInventory == null) {
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
                        if ((blockHeightDifference < 0) || (blockHeightDifference >= QueryBlocksMessage.MAX_BLOCK_HASH_COUNT)) {
                            addTupleToDownloadPlan = true;
                        }
                        else {
                            mutableUnknownBlockInventory.setLastUnknownBlockHash(blockHash);
                        }
                    }
                }
            }

            if (addTupleToDownloadPlan) {
                if (mutableUnknownBlockInventory != null) {
                    downloadPlan.add(mutableUnknownBlockInventory);
                    mutableUnknownBlockInventory = null;
                    tupleStartingBlockHeight = null;
                }
            }

            if (createNewTuple) {
                mutableUnknownBlockInventory = new MutableUnknownBlockInventory();
                mutableUnknownBlockInventory.setFirstUnknownBlockHash(blockHash);

                if (previousBlockHash == null) {
                    final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
                    final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                    if (blockId != null) {
                        final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(blockId);
                        mutableUnknownBlockInventory.setPreviousBlockHash(blockHeader.getPreviousBlockHash());
                    }
                }
                else {
                    mutableUnknownBlockInventory.setPreviousBlockHash(previousBlockHash);
                }

                tupleStartingBlockHeight = blockHeight;
            }
        }
        if (mutableUnknownBlockInventory != null) {
            downloadPlan.add(mutableUnknownBlockInventory);
        }

        return downloadPlan;
    }

    public Boolean nodesHaveBlockInventory(final List<NodeId> connectedNodeIds, final Sha256Hash blockHash) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT * FROM node_blocks_inventory WHERE node_blocks_inventory.hash = ? AND node_blocks_inventory.node_id IN (?) LIMIT 1")
                .setParameter(blockHash)
                .setInClauseParameters(connectedNodeIds, ValueExtractor.IDENTIFIER)
        );

        return (! rows.isEmpty());
    }

    public Boolean hasUnknownHighPriorityInventory(final List<NodeId> connectedNodeIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT 1 FROM pending_blocks WHERE pending_blocks.priority < 1000 AND NOT EXISTS (SELECT 1 FROM node_blocks_inventory WHERE node_blocks_inventory.hash = pending_blocks.hash AND node_blocks_inventory.node_id IN (?))")
                .setInClauseParameters(connectedNodeIds, ValueExtractor.IDENTIFIER)
        );

        return (! rows.isEmpty());
    }

    public Boolean hasUnknownInventory(final List<NodeId> connectedNodeIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT 1 FROM pending_blocks WHERE NOT EXISTS (SELECT 1 FROM node_blocks_inventory WHERE node_blocks_inventory.hash = pending_blocks.hash AND node_blocks_inventory.node_id IN (?))")
                .setInClauseParameters(connectedNodeIds, ValueExtractor.IDENTIFIER)
        );

        return (! rows.isEmpty());
    }

    public Map<PendingBlockId, NodeId> selectIncompletePendingBlocks(final List<NodeId> connectedNodeIds, final Integer maxBlockCount) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Long minSecondsBetweenDownloadAttempts = 5L;
        final Long currentTimestamp = _systemTime.getCurrentTimeInSeconds();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT node_blocks_inventory.node_id, pending_blocks.id AS pending_block_id FROM pending_blocks INNER JOIN node_blocks_inventory ON node_blocks_inventory.hash = pending_blocks.hash WHERE pending_blocks.was_downloaded = 0 AND (? - COALESCE(last_download_attempt_timestamp, 0)) > ? AND node_blocks_inventory.node_id IN (?) GROUP BY pending_blocks.id ORDER BY pending_blocks.priority ASC, pending_blocks.id ASC LIMIT " + Util.coalesce(maxBlockCount, Integer.MAX_VALUE))
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

    public PendingBlockId selectCandidatePendingBlockId() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT pending_blocks.id, pending_blocks.hash FROM pending_blocks INNER JOIN blocks ON blocks.hash = pending_blocks.previous_block_hash INNER JOIN block_transactions ON block_transactions.block_id = blocks.id WHERE pending_blocks.was_downloaded = 1 AND NOT EXISTS (SELECT 1 FROM invalid_blocks WHERE hash = pending_blocks.hash AND process_count > 2) GROUP BY block_transactions.block_id ORDER BY pending_blocks.priority ASC LIMIT 128")
        );

        for (final Row row : rows) {
            final PendingBlockId pendingBlockId = PendingBlockId.wrap(row.getLong("id"));
            if (_hasBlockData(pendingBlockId)) {
                return pendingBlockId;
            }
        }

        return null;
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

    /**
     * Deletes any pending blocks that haven't been downloaded and do not have a peer to download them from.
     *  This can happen when a peer broadcasting block inventory disconnects and there are no other peers aware of their chain.
     */
    public void purgeUnlocatablePendingBlocks(final List<NodeId> connectedNodeIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            // "Delete any pending_blocks that have not already been downloaded and do not have a connected node to download from..."
            new Query("SELECT pending_blocks.id FROM pending_blocks LEFT OUTER JOIN node_blocks_inventory ON (node_blocks_inventory.hash = pending_blocks.hash AND node_blocks_inventory.node_id IN (?)) WHERE node_blocks_inventory.id IS NULL AND pending_blocks.was_downloaded = 0")
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

    public PendingBlock getPendingBlock(final PendingBlockId pendingBlockId) throws DatabaseException {
        return _getPendingBlock(pendingBlockId, true);
    }

    public void deletePendingBlock(final PendingBlockId pendingBlockId) throws DatabaseException {
        _deletePendingBlock(pendingBlockId);
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
}