package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.MasterDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.MasterDatabaseManagerCacheCore;
import com.softwareverde.bitcoin.server.database.cache.ReadOnlyLocalDatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.BlockProcessor;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode.FullNodePendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.OrphanedTransactionsCache;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorFactory;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BlockchainBuilderTests extends IntegrationTest {
    static class FakeBlockDownloadRequester implements BlockDownloadRequester {
        @Override
        public void requestBlock(final BlockHeader blockHeader) { }

        @Override
        public void requestBlock(final Sha256Hash blockHash, final Long priority) { }

        @Override
        public void requestBlock(final Sha256Hash blockHash) { }
    }

    @Before
    public void setup() {
        _resetDatabase();
    }

    @Test
    public void should_synchronize_pending_blocks() throws Exception {
        // Setup
        try (final DatabaseConnectionFactory databaseConnectionFactory = _database.getDatabaseConnectionFactory();
             final MasterDatabaseManagerCache masterCache = new MasterDatabaseManagerCacheCore();
             final DatabaseManagerCache databaseCache = new ReadOnlyLocalDatabaseManagerCache(masterCache);
             final DatabaseConnection databaseConnection = databaseConnectionFactory.newConnection();
             final FullNodeDatabaseManager databaseManager = new FullNodeDatabaseManager(databaseConnection, databaseCache);
         ) {
            final FullNodeDatabaseManagerFactory databaseManagerFactory = new FullNodeDatabaseManagerFactory(databaseConnectionFactory, databaseCache);

            final BlockInflater blockInflater = new BlockInflater();
            final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));

            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockDatabaseManager.storeBlock(genesisBlock);
            }

            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();
            for (final String blockData : new String[] { BlockData.MainChain.BLOCK_1, BlockData.MainChain.BLOCK_2, BlockData.MainChain.BLOCK_3, BlockData.MainChain.BLOCK_4, BlockData.MainChain.BLOCK_5 }) {
                final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
                pendingBlockDatabaseManager.storeBlock(block);
            }

            final BlockchainBuilder blockchainBuilder;
            {
                final NetworkTime networkTime = new MutableNetworkTime();
                final MutableMedianBlockTime medianBlockTime = new MutableMedianBlockTime();
                final BitcoinNodeManager nodeManager = new FakeBitcoinNodeManager();

                final OrphanedTransactionsCache orphanedTransactionsCache = new OrphanedTransactionsCache(databaseCache);

                final TransactionValidatorFactory transactionValidatorFactory = new TransactionValidatorFactory();
                final BlockProcessor blockProcessor = new BlockProcessor(databaseConnectionFactory, masterCache, transactionValidatorFactory, networkTime, medianBlockTime, orphanedTransactionsCache);
                final SleepyService.StatusMonitor blockDownloaderStatusMonitor = new SleepyService.StatusMonitor() {
                    @Override
                    public SleepyService.Status getStatus() {
                        return SleepyService.Status.ACTIVE;
                    }
                };
                blockchainBuilder = new BlockchainBuilder(nodeManager, databaseManagerFactory, blockProcessor, blockDownloaderStatusMonitor, new FakeBlockDownloadRequester(), _threadPool);
            }

            Assert.assertTrue(blockchainBuilder._hasGenesisBlock);

            // Action
            blockchainBuilder.start();
            Thread.sleep(1000L);
            blockchainBuilder.stop();

            // Assert
            final BlockchainSegmentId blockchainSegmentId = BlockchainSegmentId.wrap(1L);
            Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, 1L));
            Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, 2L));
            Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, 3L));
            Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, 4L));
            Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, 5L));
        }
    }
}

class FakeBitcoinNodeManager extends BitcoinNodeManager {

    private static Properties _createFakeProperties() {
        final Properties properties = new Properties();
        properties.maxNodeCount = 0;
        return properties;
    }

    public FakeBitcoinNodeManager() {
        super(_createFakeProperties());
    }

    @Override
    public List<BitcoinNode> getNodes() {
        return new MutableList<BitcoinNode>(0);
    }

    @Override
    public void broadcastBlockFinder(final List<Sha256Hash> blockFinderHashes) { }

    @Override
    public void transmitBlockHash(final BitcoinNode bitcoinNode, final Sha256Hash blockHash) { }
}