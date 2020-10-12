package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.node.fullnode.FullNodeBitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;

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
            final FullNodeBitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();

            final int blockHashCount = blockHashes.getCount();
            final Sha256Hash firstBlockHash = blockHashes.get(0);
            final Sha256Hash lastBlockHash = blockHashes.get(blockHashCount - 1);

            final BlockId lastBlockId = blockHeaderDatabaseManager.getBlockHeaderId(lastBlockHash);
            if (lastBlockId != null) {
                final Long lastBlockHeight = blockHeaderDatabaseManager.getBlockHeight(lastBlockId);
                final Boolean isNewHeightForNode = nodeDatabaseManager.updateBlockInventory(bitcoinNode, lastBlockHeight, lastBlockHash);

                Logger.debug("HeadBlock for " + bitcoinNode + " height=" + lastBlockHeight + " hash=" + lastBlockHash);

                storeBlockHashesResult.nodeInventoryWasUpdated = isNewHeightForNode;
                storeBlockHashesResult.newBlockHashWasReceived = false;
            }
            else {
                final BlockId firstBlockId = blockHeaderDatabaseManager.getBlockHeaderId(firstBlockHash);
                if (firstBlockId != null) {
                    final Long firstBlockHeight = blockHeaderDatabaseManager.getBlockHeight(firstBlockId);

                    final BlockInventoryMessageHandlerUtil.NodeInventory nodeInventory = BlockInventoryMessageHandlerUtil.getHeadBlockInventory(firstBlockHeight, blockHashes, new BlockInventoryMessageHandlerUtil.BlockIdStore() {
                        @Override
                        public BlockId getBlockId(final Sha256Hash blockHash) throws Exception {
                            return blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                        }
                    });

                    Logger.debug("HeadBlock for " + bitcoinNode + " height=" + nodeInventory.blockHeight + " hash=" + nodeInventory.blockHash);

                    final Boolean isNewHeightForNode = nodeDatabaseManager.updateBlockInventory(bitcoinNode, nodeInventory.blockHeight, nodeInventory.blockHash);
                    storeBlockHashesResult.nodeInventoryWasUpdated = isNewHeightForNode;
                    storeBlockHashesResult.newBlockHashWasReceived = true;
                }
                else {
                    Logger.debug("Unknown HeadBlock for " + bitcoinNode + " hash=" + firstBlockHash);

                    // The block hash does not match a known header, therefore its block height cannot be determined and it is ignored.
                    storeBlockHashesResult.nodeInventoryWasUpdated = false;
                    storeBlockHashesResult.newBlockHashWasReceived = false;
                }
            }
        }
        catch (final Exception exception) {
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
