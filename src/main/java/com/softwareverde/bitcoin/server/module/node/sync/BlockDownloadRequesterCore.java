package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode.FullNodePendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.type.time.SystemTime;

/**
 * BlockDownloadRequesterCore stores a PendingBlock record for the requested Block and notifies the BlockDownloader.
 *  If the block is high-priority and no peers currently have the block, a blockfinder is emitted.
 */
public class BlockDownloadRequesterCore implements BlockDownloadRequester {
    protected final SystemTime _systemTime = new SystemTime();

    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    protected final BlockDownloader _blockDownloader;
    protected final BitcoinNodeManager _bitcoinNodeManager;

    protected final Object _lastUnavailableRequestedBlockTimestampMutex = new Object();
    protected Long _lastUnavailableRequestedBlockTimestamp = 0L;

    protected Sha256Hash _getParentBlockHash(final Sha256Hash childBlockHash, final DatabaseManager databaseManager) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
        final BlockId childBlockId = blockHeaderDatabaseManager.getBlockHeaderId(childBlockHash);
        if (childBlockId == null) { return null; }

        final BlockId parentBlockId = blockHeaderDatabaseManager.getAncestorBlockId(childBlockId, 1);
        if (parentBlockId == null) { return null; }

        return blockHeaderDatabaseManager.getBlockHash(parentBlockId);
    }

    protected void _requestBlock(final Sha256Hash blockHash, final Sha256Hash parentBlockHash, final Long priority) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();

            TransactionUtil.startTransaction(databaseConnection);
            final PendingBlockId pendingBlockId = pendingBlockDatabaseManager.storeBlockHash(blockHash, parentBlockHash);
            pendingBlockDatabaseManager.setPriority(pendingBlockId, priority);
            TransactionUtil.commitTransaction(databaseConnection);

            if (priority < 256) { // Check if any peers have the requested block if it is of high priority...
                // If none of the nodes have the block in their known inventory, ask the peers specifically for the block.

                final boolean searchForBlockHash = true;
//                boolean searchForBlockHash = false;
//                synchronized (_lastUnavailableRequestedBlockTimestampMutex) {
//                    final Long now = _systemTime.getCurrentTimeInSeconds();
//                    final long durationSinceLastRequest = (now - _lastUnavailableRequestedBlockTimestamp);
//                    if (durationSinceLastRequest > 10L) { // Limit the frequency of QueryBlock/BlockFinder broadcasts to once every 10 seconds...
//                        final List<NodeId> connectedNodes = _bitcoinNodeManager.getNodeIds();
//                        final Boolean nodesHaveInventory = pendingBlockDatabaseManager.nodesHaveBlockInventory(connectedNodes, blockHash);
//                        if (! nodesHaveInventory) {
//                            _lastUnavailableRequestedBlockTimestamp = now;
//                            searchForBlockHash = true;
//                        }
//                    }
//                }

                if (searchForBlockHash) {
                    // Use the previousBlockHash (if provided)...
                    final MutableList<Sha256Hash> blockFinderHashes = new MutableList<Sha256Hash>(1);
                    if (parentBlockHash != null) {
                        blockFinderHashes.add(parentBlockHash);
                        Logger.debug("Broadcasting QueryBlocks with provided BlockHash: " + parentBlockHash);
                    }
                    else {
                        // Search for the previousBlockHash via the database (relies on the BlockHeaders sync)...
                        final Sha256Hash queriedParentBlockHash = _getParentBlockHash(blockHash, databaseManager);
                        if (queriedParentBlockHash != null) {
                            blockFinderHashes.add(queriedParentBlockHash);
                            Logger.debug("Broadcasting QueryBlocks with queried BlockHash: " + queriedParentBlockHash);
                        }
                        else {
                            // Fallback to broadcasting a blockFinder...
                            final BlockFinderHashesBuilder blockFinderHashesBuilder = new BlockFinderHashesBuilder(databaseManager);
                            blockFinderHashes.addAll(blockFinderHashesBuilder.createBlockFinderBlockHashes());
                            Logger.debug("Broadcasting blockfinder...");
                        }
                    }

                    _bitcoinNodeManager.broadcastBlockFinder(blockFinderHashes);
                }
            }

            _blockDownloader.wakeUp();
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
        }
    }

    public BlockDownloadRequesterCore(final FullNodeDatabaseManagerFactory databaseManagerFactory, final BlockDownloader blockDownloader, final BitcoinNodeManager bitcoinNodeManager) {
        _databaseManagerFactory = databaseManagerFactory;
        _blockDownloader = blockDownloader;
        _bitcoinNodeManager = bitcoinNodeManager;
    }

    @Override
    public void requestBlock(final BlockHeader blockHeader) {
        final Sha256Hash blockHash = blockHeader.getHash();
        final Sha256Hash previousBlockHash = blockHeader.getPreviousBlockHash();
        final Long timestamp = blockHeader.getTimestamp();
        _requestBlock(blockHash, previousBlockHash, timestamp);
    }

    @Override
    public void requestBlock(final Sha256Hash blockHash, final Sha256Hash previousBlockHash) {
        _requestBlock(blockHash, previousBlockHash, 0L);
    }
}
