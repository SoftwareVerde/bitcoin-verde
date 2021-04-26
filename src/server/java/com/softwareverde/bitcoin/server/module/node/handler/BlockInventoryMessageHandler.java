package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.node.fullnode.FullNodeBitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.manager.banfilter.BanFilter;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

public class BlockInventoryMessageHandler implements BitcoinNode.BlockInventoryAnnouncementHandler {
    public interface NewInventoryReceivedCallback {
        default void onNewBlockHashesReceived(List<Sha256Hash> blockHashes) { }
        default void onNewBlockHeadersReceived(BitcoinNode bitcoinNode, List<BlockHeader> blockHeaders) { }
    }

    protected static class StoreBlockHashesResult {
        public Boolean nodeInventoryWasUpdated = false;
        public Boolean newBlockHashWasReceived = false;
        public Long maxBlockHeight = null;
    }

    public static final BitcoinNode.BlockInventoryAnnouncementHandler IGNORE_INVENTORY_HANDLER = new BitcoinNode.BlockInventoryAnnouncementHandler() {
        @Override
        public void onNewInventory(final BitcoinNode bitcoinNode, final List<Sha256Hash> blockHashes) { }

        @Override
        public void onNewHeaders(final BitcoinNode bitcoinNode, final List<BlockHeader> blockHeaders) { }
    };

    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final SynchronizationStatus _synchronizationStatus;
    protected final BanFilter _banFilter;

    protected NewInventoryReceivedCallback _newInventoryReceivedCallback;
    protected Runnable _nodeInventoryUpdatedCallback;

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
                storeBlockHashesResult.maxBlockHeight = lastBlockHeight;
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

    public BlockInventoryMessageHandler(final FullNodeDatabaseManagerFactory databaseManagerFactory, final SynchronizationStatus synchronizationStatus, final BanFilter banFilter) {
        _databaseManagerFactory = databaseManagerFactory;
        _synchronizationStatus = synchronizationStatus;
        _banFilter = banFilter;
    }

    public void setNewInventoryReceivedCallback(final NewInventoryReceivedCallback newInventoryReceivedCallback) {
        _newInventoryReceivedCallback = newInventoryReceivedCallback;
    }

    public void setNodeInventoryUpdatedCallback(final Runnable nodeInventoryUpdatedCallback) {
        _nodeInventoryUpdatedCallback = nodeInventoryUpdatedCallback;
    }

    @Override
    public void onNewInventory(final BitcoinNode bitcoinNode, final List<Sha256Hash> blockHashes) {
        if (_banFilter != null) {
            final Boolean shouldAcceptInventory = _banFilter.onInventoryReceived(bitcoinNode, blockHashes);
            if (! shouldAcceptInventory) {
                Logger.info("Received invalid inventory from " + bitcoinNode + ".");
                bitcoinNode.disconnect();
                return;
            }
        }

        final StoreBlockHashesResult storeBlockHashesResult = _storeBlockHashes(bitcoinNode, blockHashes);
        if (storeBlockHashesResult.maxBlockHeight != null) {
            final Long nodeBlockHeight = bitcoinNode.getBlockHeight();
            if (storeBlockHashesResult.maxBlockHeight > Util.coalesce(nodeBlockHeight)) {
                bitcoinNode.setBlockHeight(storeBlockHashesResult.maxBlockHeight);
            }
        }

        if (storeBlockHashesResult.newBlockHashWasReceived) {
            final NewInventoryReceivedCallback newBlockHashesCallback = _newInventoryReceivedCallback;
            if (newBlockHashesCallback != null) {
                newBlockHashesCallback.onNewBlockHashesReceived(blockHashes);
            }
        }
        else if (storeBlockHashesResult.nodeInventoryWasUpdated) {
            final Runnable inventoryUpdatedCallback = _nodeInventoryUpdatedCallback;
            if (inventoryUpdatedCallback != null) {
                inventoryUpdatedCallback.run();
            }
        }
    }

    @Override
    public void onNewHeaders(final BitcoinNode bitcoinNode, final List<BlockHeader> blockHeaders) {
        Long maxBlockHeight = null;
        final MutableList<BlockHeader> unknownBlockHeaders = new MutableList<BlockHeader>();
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            boolean hasUnknownHeader = false;
            for (final BlockHeader blockHeader : blockHeaders) {
                final Sha256Hash blockHash = blockHeader.getHash();
                final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                if (blockId == null) {
                    hasUnknownHeader = true;
                }
                else {
                    final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
                    if (Util.coalesce(blockHeight) > Util.coalesce(maxBlockHeight)) {
                        maxBlockHeight = blockHeight;
                    }
                }

                if (hasUnknownHeader) {
                    unknownBlockHeaders.add(blockHeader);
                }
            }
        }
        catch (final Exception exception) {
            Logger.warn(exception);
            return;
        }

        if (maxBlockHeight != null) {
            final Long nodeBlockHeight = bitcoinNode.getBlockHeight();
            if (Util.coalesce(nodeBlockHeight) < maxBlockHeight) {
                bitcoinNode.setBlockHeight(maxBlockHeight);
            }
        }

        if (! unknownBlockHeaders.isEmpty()) {
            final NewInventoryReceivedCallback newBlockHashesCallback = _newInventoryReceivedCallback;
            if (newBlockHashesCallback != null) {
                newBlockHashesCallback.onNewBlockHeadersReceived(bitcoinNode, unknownBlockHeaders);
            }
        }
    }
}
