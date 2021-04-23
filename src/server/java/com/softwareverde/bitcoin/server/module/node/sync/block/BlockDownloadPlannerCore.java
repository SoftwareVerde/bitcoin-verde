package com.softwareverde.bitcoin.server.module.node.sync.block;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStore;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.timer.NanoTimer;

public class BlockDownloadPlannerCore implements BlockDownloader.BlockDownloadPlanner {
    protected final PendingBlockStore _blockStore;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    public BlockDownloadPlannerCore(final FullNodeDatabaseManagerFactory databaseManagerFactory, final PendingBlockStore blockStore) {
        _blockStore = blockStore;
        _databaseManagerFactory = databaseManagerFactory;
    }

    @Override
    public List<BlockDownloader.PendingBlockInventory> getNextPendingBlockInventoryBatch() {
        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        final int batchSize = 256;
        final MutableList<BlockDownloader.PendingBlockInventory> pendingBlockInventoryBatch = new MutableList<>();

        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

            final BlockId headBlockId;
            final Long headBlockHeight;
            {
                final BlockId nullableHeadBlockId = blockDatabaseManager.getHeadBlockId();
                if (nullableHeadBlockId != null) {
                    headBlockId = nullableHeadBlockId;
                }
                else {
                    final BlockDownloader.PendingBlockInventory pendingBlockInventory = new BlockDownloader.PendingBlockInventory(0L, BlockHeader.GENESIS_BLOCK_HASH);
                    pendingBlockInventoryBatch.add(pendingBlockInventory);
                    headBlockId = blockHeaderDatabaseManager.getBlockHeaderId(BlockHeader.GENESIS_BLOCK_HASH);
                }

                headBlockHeight = (headBlockId != null ? blockHeaderDatabaseManager.getBlockHeight(headBlockId) : null);
            }

            if (headBlockId != null) {
                BlockId previousBlockId = headBlockId;
                for (int i = 0; i < batchSize; ++i) {
                    final BlockId blockId = blockHeaderDatabaseManager.getChildBlockId(headBlockchainSegmentId, previousBlockId);
                    if (blockId == null) { break; }

                    final Sha256Hash blockHash = blockHeaderDatabaseManager.getBlockHash(blockId);
                    final Long blockHeight = (headBlockHeight + i);

                    final Boolean pendingBlockExists = _blockStore.pendingBlockExists(blockHash);
                    if (! pendingBlockExists) {
                        final BlockDownloader.PendingBlockInventory pendingBlockInventory = new BlockDownloader.PendingBlockInventory(blockHeight, blockHash, null, blockHeight);
                        pendingBlockInventoryBatch.add(pendingBlockInventory);
                    }

                    previousBlockId = blockId;
                }
            }
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
        }

        nanoTimer.stop();
        Logger.trace("Determined next BlockDownloader batch in " + nanoTimer.getMillisecondsElapsed() + "ms.");

        return pendingBlockInventoryBatch;
    }
}
