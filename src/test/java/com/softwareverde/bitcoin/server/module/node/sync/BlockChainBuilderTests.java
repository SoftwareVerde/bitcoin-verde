package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.PendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.MasterDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.ReadOnlyLocalDatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.BlockProcessor;
import com.softwareverde.bitcoin.server.module.node.SleepyService;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BlockChainBuilderTests extends IntegrationTest {
    static class FakeBlockDownloadRequester extends BlockDownloadRequester {
        @Override
        protected void _requestBlock(final Sha256Hash blockHash, final Long priority) {
            // Nothing.
        }

        public FakeBlockDownloadRequester() {
            super(null, null);
        }
    }

    @Before
    public void setup() {
        _resetDatabase();
    }

    @Test
    public void should_synchronize_pending_blocks() throws Exception {
        // Setup
        final MasterDatabaseManagerCache masterCache = new MasterDatabaseManagerCache();
        final DatabaseManagerCache databaseCache = new ReadOnlyLocalDatabaseManagerCache(masterCache);

        final MysqlDatabaseConnectionFactory databaseConnectionFactory = _database.getDatabaseConnectionFactory();

        final BlockInflater blockInflater = new BlockInflater();
        final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));

        final MysqlDatabaseConnection databaseConnection = databaseConnectionFactory.newConnection();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, databaseCache);

        blockDatabaseManager.storeBlock(genesisBlock);

        final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);
        for (final String blockData : new String[] { BlockData.MainChain.BLOCK_1, BlockData.MainChain.BLOCK_2, BlockData.MainChain.BLOCK_3, BlockData.MainChain.BLOCK_4, BlockData.MainChain.BLOCK_5 }) {
            final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
            pendingBlockDatabaseManager.storeBlock(block);
        }

        final BlockChainBuilder blockChainBuilder;
        {
            final NetworkTime networkTime = new MutableNetworkTime();
            final MutableMedianBlockTime medianBlockTime = new MutableMedianBlockTime();
            final BitcoinNodeManager nodeManager = null;

            final BlockProcessor blockProcessor = new BlockProcessor(databaseConnectionFactory, masterCache, networkTime, medianBlockTime);
            final SleepyService.StatusMonitor blockDownloaderStatusMonitor = new SleepyService.StatusMonitor() {
                @Override
                public SleepyService.Status getStatus() {
                    return SleepyService.Status.ACTIVE;
                }
            };
            blockChainBuilder = new BlockChainBuilder(nodeManager, databaseConnectionFactory, databaseCache, blockProcessor, blockDownloaderStatusMonitor, new FakeBlockDownloadRequester());
        }

        Assert.assertTrue(blockChainBuilder._hasGenesisBlock);

        // Action
        blockChainBuilder.start();
        Thread.sleep(1000L);
        blockChainBuilder.stop();

        // Assert
        final BlockChainSegmentId blockChainSegmentId = BlockChainSegmentId.wrap(1L);
        Assert.assertNotNull(blockDatabaseManager.getBlockIdAtHeight(blockChainSegmentId, 1L));
        Assert.assertNotNull(blockDatabaseManager.getBlockIdAtHeight(blockChainSegmentId, 2L));
        Assert.assertNotNull(blockDatabaseManager.getBlockIdAtHeight(blockChainSegmentId, 3L));
        Assert.assertNotNull(blockDatabaseManager.getBlockIdAtHeight(blockChainSegmentId, 4L));
        Assert.assertNotNull(blockDatabaseManager.getBlockIdAtHeight(blockChainSegmentId, 5L));
    }
}
