package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode.FullNodePendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.node.fullnode.FullNodeBitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;

public class BlockInventoryMessageHandler implements BitcoinNode.BlockInventoryMessageCallback {
    public static final BitcoinNode.BlockInventoryMessageCallback IGNORE_INVENTORY_HANDLER = new BitcoinNode.BlockInventoryMessageCallback() {
        @Override
        public void onResult(final BitcoinNode bitcoinNode, final List<Sha256Hash> blockHashes) { }
    };

    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final SynchronizationStatus _synchronizationStatus;

    protected Runnable _newBlockHashReceivedCallback;
    protected Runnable _nodeInventoryUpdatedCallback;

    static class StoreBlockHashesResult {
        public Boolean nodeInventoryWasUpdated = false;
        public Boolean newBlockHashWasReceived = false;
    }

    protected StoreBlockHashesResult _storeBlockHashes(final BitcoinNode bitcoinNode, final List<Sha256Hash> blockHashes) {
        final StoreBlockHashesResult storeBlockHashesResult = new StoreBlockHashesResult();
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();
            final FullNodeBitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();

            final ImmutableListBuilder<PendingBlockId> pendingBlockIds = new ImmutableListBuilder<PendingBlockId>(blockHashes.getCount());
            Sha256Hash previousBlockHash = null;
            for (final Sha256Hash blockHash : blockHashes) {
                if (previousBlockHash == null) {
                    final Boolean headerExists = blockHeaderDatabaseManager.blockHeaderExists(blockHash);
                    if (headerExists) {
                        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                        final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(blockId);
                        previousBlockHash = blockHeader.getPreviousBlockHash();
                    }
                }

                final Boolean blockExists = blockDatabaseManager.hasTransactions(blockHash);
                if (blockExists) {
                    previousBlockHash = blockHash;
                    continue;
                }

                final Boolean pendingBlockExists = pendingBlockDatabaseManager.pendingBlockExists(blockHash);
                if (! pendingBlockExists) {
                    storeBlockHashesResult.newBlockHashWasReceived = true;
                }

                final PendingBlockId pendingBlockId = pendingBlockDatabaseManager.storeBlockHash(blockHash, previousBlockHash);

                if (pendingBlockId != null) {
                    pendingBlockIds.add(pendingBlockId);
                }

                previousBlockHash = blockHash;
            }

            try {
                storeBlockHashesResult.nodeInventoryWasUpdated = nodeDatabaseManager.updateBlockInventory(bitcoinNode, blockHashes);
            }
            catch (final DatabaseException databaseException) {
                Logger.debug("Deadlock encountered while trying to update BlockInventory for host: " + bitcoinNode.getConnectionString());
            }

        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }

        return storeBlockHashesResult;
    }

    public BlockInventoryMessageHandler(final FullNodeDatabaseManagerFactory databaseManagerFactory, final SynchronizationStatus synchronizationStatus) {
        _databaseManagerFactory = databaseManagerFactory;
        _synchronizationStatus = synchronizationStatus;
    }

    public void setNewBlockHashReceivedCallback(final Runnable newBlockHashesCallback) {
        _newBlockHashReceivedCallback = newBlockHashesCallback;
    }

    public void setNodeInventoryUpdatedCallback(final Runnable nodeInventoryUpdatedCallback) {
        _nodeInventoryUpdatedCallback = nodeInventoryUpdatedCallback;
    }

    @Override
    public void onResult(final BitcoinNode bitcoinNode, final List<Sha256Hash> blockHashes) {
        final StoreBlockHashesResult storeBlockHashesResult = _storeBlockHashes(bitcoinNode, blockHashes);

//        // NOTE: Exploring alternate forks should only be done after the initial sync is complete...
//        final State state = _synchronizationStatus.getState();
//        if (state == State.ONLINE) { // If the inventory message has new blocks or the last block is not on the main blockchain, then request block hashes after the most recent hash to continue synchronizing blocks (even a minority fork)...
//            final Sha256Hash mostRecentBlockHash = blockHashes.get(blockHashes.getSize() - 1);
//
//            Boolean mostRecentBlockIsMemberOfHeadBlockchain = true;
//
//            if (blockHashes.getSize() > 1) {
//                mostRecentBlockIsMemberOfHeadBlockchain = false;
//
//                try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
//                    final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(databaseConnection, _databaseCache);
//                    final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseCache);
//
//                    final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(mostRecentBlockHash);
//                    if (blockId != null) {
//                        final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
//                        mostRecentBlockIsMemberOfHeadBlockchain = blockHeaderDatabaseManager.isBlockConnectedToChain(blockId, headBlockchainSegmentId, BlockRelationship.ANY);
//                    }
//                }
//                catch (final DatabaseException databaseException) {
//                    Logger.warn(databaseException);
//                }
//            }
//
//            if ( (storeBlockHashesResult.newBlockHashWasReceived) || (! mostRecentBlockIsMemberOfHeadBlockchain) ) {
//                bitcoinNode.requestBlockHashesAfter(mostRecentBlockHash);
//            }
//        }

        if (storeBlockHashesResult.newBlockHashWasReceived) {
            final Runnable newBlockHashesCallback = _newBlockHashReceivedCallback;
            if (newBlockHashesCallback != null) {
                newBlockHashesCallback.run();
            }
        }
        else if (storeBlockHashesResult.nodeInventoryWasUpdated) {
            final Runnable inventoryUpdatedCallback = _nodeInventoryUpdatedCallback;
            if (inventoryUpdatedCallback != null) {
                inventoryUpdatedCallback.run();
            }
        }
    }
}
