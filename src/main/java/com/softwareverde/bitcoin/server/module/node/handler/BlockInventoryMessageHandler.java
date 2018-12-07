package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.database.*;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;

public class BlockInventoryMessageHandler implements BitcoinNode.BlockInventoryMessageCallback {
    public static final BitcoinNode.BlockInventoryMessageCallback IGNORE_INVENTORY_HANDLER = new BitcoinNode.BlockInventoryMessageCallback() {
        @Override
        public void onResult(final BitcoinNode bitcoinNode, final List<Sha256Hash> blockHashes) { }
    };

    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseCache;
    protected Runnable _newBlockHashReceivedCallback;
    protected Runnable _nodeInventoryUpdatedCallback;

    static class StoreBlockHashesResult {
        public Boolean nodeInventoryWasUpdated = false;
        public Boolean newBlockHashWasReceived = false;
    }

    protected StoreBlockHashesResult _storeBlockHashes(final BitcoinNode bitcoinNode, final List<Sha256Hash> blockHashes) {
        final StoreBlockHashesResult storeBlockHashesResult = new StoreBlockHashesResult();
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseCache);

            final ImmutableListBuilder<PendingBlockId> pendingBlockIds = new ImmutableListBuilder<PendingBlockId>(blockHashes.getSize());
            for (final Sha256Hash blockHash : blockHashes) {
                // NOTE: The order of the "does-exist" checks matter in order to prevent a race condition between this callback and the BlockchainSynchronizer...
                final Boolean blockExists = blockDatabaseManager.blockHeaderHasTransactions(blockHash);
                if (blockExists) { continue; }

                final PendingBlockId pendingBlockId;
                final Boolean pendingBlockExists = pendingBlockDatabaseManager.pendingBlockExists(blockHash);
                if (! pendingBlockExists) {
                    pendingBlockId = pendingBlockDatabaseManager.storeBlockHash(blockHash);

                    if (pendingBlockId != null) {
                        storeBlockHashesResult.newBlockHashWasReceived = true;
                    }
                }
                else {
                    pendingBlockId = pendingBlockDatabaseManager.getPendingBlockId(blockHash);
                }

                if (pendingBlockId != null) {
                    pendingBlockIds.add(pendingBlockId);
                }
            }

            final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);
            storeBlockHashesResult.nodeInventoryWasUpdated = nodeDatabaseManager.updateBlockInventory(bitcoinNode, pendingBlockIds.build());

        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }

        return storeBlockHashesResult;
    }

    public BlockInventoryMessageHandler(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseCache) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseCache = databaseCache;
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

        { // If the inventory message has new blocks or the last block is not on the main blockchain, then request block hashes after the most recent hash to continue synchronizing blocks (even a minority fork)...
            final Sha256Hash mostRecentBlockHash = blockHashes.get(blockHashes.getSize() - 1);

            Boolean mostRecentBlockIsMemberOfHeadBlockchain = true;

            if (blockHashes.getSize() > 1) {
                mostRecentBlockIsMemberOfHeadBlockchain = false;

                try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                    final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(databaseConnection, _databaseCache);
                    final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseCache);

                    final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(mostRecentBlockHash);
                    if (blockId != null) {
                        final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
                        mostRecentBlockIsMemberOfHeadBlockchain = blockHeaderDatabaseManager.isBlockConnectedToChain(blockId, headBlockchainSegmentId, BlockRelationship.ANY);
                    }
                }
                catch (final DatabaseException databaseException) {
                    Logger.log(databaseException);
                }
            }

            if ( (storeBlockHashesResult.newBlockHashWasReceived) || (! mostRecentBlockIsMemberOfHeadBlockchain) ) {
                bitcoinNode.requestBlockHashesAfter(mostRecentBlockHash);
            }
        }

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
