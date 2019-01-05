package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.database.PendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.util.type.time.SystemTime;

public class BlockDownloadRequester {
    protected final SystemTime _systemTime = new SystemTime();
    protected final MysqlDatabaseConnectionFactory _connectionFactory;
    protected final BlockDownloader _blockDownloader;
    protected final BitcoinNodeManager _bitcoinNodeManager;
    protected final DatabaseManagerCache _databaseCache;

    protected final Object _lastUnavailableRequestedBlockTimestampMutex = new Object();
    protected Long _lastUnavailableRequestedBlockTimestamp = 0L;
    protected final Object _lastNodeInventoryBroadcastTimestampMutex = new Object();
    protected Long _lastNodeInventoryBroadcastTimestamp = 0L;

    protected Sha256Hash _getParentBlockHash(final Sha256Hash childBlockHash, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseCache);
        final BlockId childBlockId = blockHeaderDatabaseManager.getBlockHeaderId(childBlockHash);
        if (childBlockId == null) { return null; }

        final BlockId parentBlockId = blockHeaderDatabaseManager.getAncestorBlockId(childBlockId, 1);
        if (parentBlockId == null) { return null; }

        return blockHeaderDatabaseManager.getBlockHash(parentBlockId);
    }

    protected void _requestBlock(final Sha256Hash blockHash, final Sha256Hash parentBlockHash, final Long priority) {
        try (final MysqlDatabaseConnection databaseConnection = _connectionFactory.newConnection()) {
            final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);
            final PendingBlockId pendingBlockId = pendingBlockDatabaseManager.storeBlockHash(blockHash, parentBlockHash);
            pendingBlockDatabaseManager.setPriority(pendingBlockId, priority);

            if (priority < 256) { // Check if any peers have the requested block if it is of high priority...
                // If none of the nodes have the block in their known inventory, ask the peers specifically for the block.
                // TODO: Consider: If no peers still do not have the block, search for a historic node with the block.

                Boolean searchForBlockHash = false;
                synchronized (_lastUnavailableRequestedBlockTimestampMutex) {
                    final Long now = _systemTime.getCurrentTimeInSeconds();
                    final Long durationSinceLastRequest = (now - _lastUnavailableRequestedBlockTimestamp);
                    if (durationSinceLastRequest > 10L) { // Limit the frequency of QueryBlock/BlockFinder broadcasts to once every 10 seconds...
                        final List<NodeId> connectedNodes = _bitcoinNodeManager.getNodeIds();
                        final Boolean nodesHaveInventory = pendingBlockDatabaseManager.nodesHaveBlockInventory(connectedNodes, blockHash);
                        if (! nodesHaveInventory) {
                            _lastUnavailableRequestedBlockTimestamp = now;
                            searchForBlockHash = true;
                        }
                    }
                }

                if (searchForBlockHash) {
                    // Use the previousBlockHash (if provided)...
                    final MutableList<Sha256Hash> blockFinderHashes = new MutableList<Sha256Hash>(1);
                    if (parentBlockHash != null) {
                        blockFinderHashes.add(parentBlockHash);
                        Logger.log("Broadcasting QueryBlocks with provided BlockHash: " + parentBlockHash);
                    }
                    else {
                        // Search for the previousBlockHash via the database (relies on the BlockHeaders sync)...
                        final Sha256Hash queriedParentBlockHash = _getParentBlockHash(blockHash, databaseConnection);
                        if (queriedParentBlockHash != null) {
                            blockFinderHashes.add(queriedParentBlockHash);
                            Logger.log("Broadcasting QueryBlocks with queried BlockHash: " + queriedParentBlockHash);
                        }
                        else {
                            // Fallback to broadcasting a blockFinder...
                            final BlockFinderHashesBuilder blockFinderHashesBuilder = new BlockFinderHashesBuilder(databaseConnection, _databaseCache);
                            blockFinderHashes.addAll(blockFinderHashesBuilder.createBlockFinderBlockHashes());
                            Logger.log("Broadcasting blockfinder...");
                        }
                    }

                    _bitcoinNodeManager.broadcastBlockFinder(blockFinderHashes);
                }
            }

            _blockDownloader.wakeUp();
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }
    }

//    /**
//     * Check for missing NodeInventory in order to speed up the initial Block download...
//     */
//    protected void _checkForNodeInventory() {
//        synchronized (_lastNodeInventoryBroadcastTimestampMutex) {
//            final Long now = _systemTime.getCurrentTimeInSeconds();
//            final Long durationSinceLastRequest = (now - _lastNodeInventoryBroadcastTimestamp);
//            if (durationSinceLastRequest <= 30) { return; } // Throttle requests to once every 30 seconds...
//
//            _lastNodeInventoryBroadcastTimestamp = now;
//        }
//
//        _bitcoinNodeManager.findNodeInventory();
//    }

    public BlockDownloadRequester(final MysqlDatabaseConnectionFactory connectionFactory, final BlockDownloader blockDownloader, final BitcoinNodeManager bitcoinNodeManager, final DatabaseManagerCache databaseManagerCache) {
        _connectionFactory = connectionFactory;
        _blockDownloader = blockDownloader;
        _bitcoinNodeManager = bitcoinNodeManager;
        _databaseCache = databaseManagerCache;
    }

    public void requestBlock(final BlockHeader blockHeader) {
//        _checkForNodeInventory();
        _requestBlock(blockHeader.getHash(), blockHeader.getPreviousBlockHash(), blockHeader.getTimestamp());
    }

    public void requestBlock(final Sha256Hash blockHash, final Long priority) {
//        _checkForNodeInventory();
        _requestBlock(blockHash, null, priority);
    }

    public void requestBlock(final Sha256Hash blockHash) {
//        _checkForNodeInventory();
        _requestBlock(blockHash, null, 0L);
    }
}
