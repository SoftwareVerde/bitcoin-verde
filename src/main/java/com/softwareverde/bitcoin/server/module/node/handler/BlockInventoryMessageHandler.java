package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode.FullNodePendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.node.fullnode.FullNodeBitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
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
                storeBlockHashesResult.nodeInventoryWasUpdated = isNewHeightForNode;
                storeBlockHashesResult.newBlockHashWasReceived = false;
            }
            else {
                final BlockId firstBlockId = blockHeaderDatabaseManager.getBlockHeaderId(firstBlockHash);
                if (firstBlockId != null) {
                    final Long firstBlockHeight = blockHeaderDatabaseManager.getBlockHeight(firstBlockId);

                    Sha256Hash blockHash = firstBlockHash;
                    long blockHeight = firstBlockHeight;
                    // Determine which blockHash is the first unseen hash...
                    // TODO: This could be accomplished in about half as many lookups via binary search...
                    for (int i = 1; i < blockHashCount; ++i) { // NOTE: i = 1; the first blockHash was already evaluated and is therefore skipped...
                        final Sha256Hash nextBlockHash = blockHashes.get(i);
                        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(nextBlockHash);
                        if (blockId == null) { break; }

                        blockHeight += 1L;
                    }

                    final Boolean isNewHeightForNode = nodeDatabaseManager.updateBlockInventory(bitcoinNode, blockHeight, blockHash);
                    storeBlockHashesResult.nodeInventoryWasUpdated = isNewHeightForNode;
                    storeBlockHashesResult.newBlockHashWasReceived = true;
                }
                else {
                    // The block hash does not match a known header, therefore its block height cannot be determined and it is ignored.
                    storeBlockHashesResult.nodeInventoryWasUpdated = false;
                    storeBlockHashesResult.newBlockHashWasReceived = false;
                }
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
