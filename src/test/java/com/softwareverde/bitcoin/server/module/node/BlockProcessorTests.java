package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.database.cache.*;
import com.softwareverde.bitcoin.server.database.cache.utxo.UnspentTransactionOutputCacheFactory;
import com.softwareverde.bitcoin.server.database.cache.utxo.UtxoCount;
import com.softwareverde.bitcoin.server.database.pool.DatabaseConnectionPool;
import com.softwareverde.bitcoin.server.main.NativeUnspentTransactionOutputCache;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.OrphanedTransactionsCache;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorFactory;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BlockProcessorTests extends IntegrationTest {

    @Before
    public void setup() {
        _resetDatabase();
    }

    protected static void _should_maintain_correct_blockchain_segment_after_invalid_contentious_block(final DatabaseManagerCache databaseManagerCache, final MasterDatabaseManagerCache masterDatabaseManagerCache) throws Exception {
        /*
            This test emulates a found error in production on 2019-11-15 shortly after the hard fork.  While the HF did not cause the bug, it did cause
            the bug to manifest when a 10+ old block was mined that was invalid with the new HF rules.

            The cause of the bug involved a dirty read from the BlockchainSegment cache which was rolled back after the invalid block failed to process.

            The test scenario executed in this test creates the following chain of blocks:

                                      genesis (height=0, segment=1)
                                        /\
                                      /   \
         (segment=3) invalidBlock01Prime   block01 (height=1, segment=2)
                                            \
                                             block02 (height=2, segment=2)
                                             \
                                              block03 (height=3, segment=2)

            Where the insert-order is genesis -> block01 -> block02 -> invalidBlock01Prime -> block03
         */

        final DatabaseConnectionPool databaseConnectionPool = _database.getDatabaseConnectionPool();
        final FullNodeDatabaseManagerFactory databaseManagerFactory = new FullNodeDatabaseManagerFactory(databaseConnectionPool, databaseManagerCache);

        try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            // Setup
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            final BlockInflater blockInflater = new BlockInflater();
            final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockDatabaseManager.insertBlock(genesisBlock);
            }

            final MasterInflater masterInflater = new CoreInflater();
            final TransactionValidatorFactory transactionValidatorFactory = new TransactionValidatorFactory();

            final MutableNetworkTime mutableNetworkTime = new MutableNetworkTime();

            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final MutableMedianBlockTime medianBlockTime = blockHeaderDatabaseManager.initializeMedianBlockTime();

            final OrphanedTransactionsCache orphanedTransactionsCache = new OrphanedTransactionsCache(databaseManagerCache);

            final BlockProcessor blockProcessor;
            {
                blockProcessor = new BlockProcessor(databaseManagerFactory, masterDatabaseManagerCache, masterInflater, transactionValidatorFactory, mutableNetworkTime, medianBlockTime, orphanedTransactionsCache, null);
                blockProcessor.setMaxThreadCount(2);
                blockProcessor.setTrustedBlockHeight(0L);
            }

            final Block block01 = blockInflater.fromBytes(ByteArray.fromHexString(BlockData.MainChain.BLOCK_1));
            final Block block02 = blockInflater.fromBytes(ByteArray.fromHexString(BlockData.MainChain.BLOCK_2));
            final Block block03 = blockInflater.fromBytes(ByteArray.fromHexString(BlockData.MainChain.BLOCK_3));
            final Block invalidBlock01Prime = blockInflater.fromBytes(ByteArray.fromHexString("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D619000000000073387C6C752B492D7D6DA0CA48715EE10394683D4421B602E80B754657B2E0A79130D05DFFFF001DE339AB7E0201000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D0104FFFFFFFF0100F2052A0100000043410496B538E853519C726A2C91E61EC11600AE1390813A627C66FB8BE7947BE63C52DA7589379515D4E0A604F8141781E62294721166BF621E73A82CBF2342C858EEAC0000000002000000013BA3EDFD7A7B12B27AC72C3E67768F617FC81BC3888A51323A9FB8AA4B1E5E4A0000000000FFFFFFFF0100F2052A0100000043410496B538E853519C726A2C91E61EC11600AE1390813A627C66FB8BE7947BE63C52DA7589379515D4E0A604F8141781E62294721166BF621E73A82CBF2342C858EEAC00000000"));

            final Runnable blockchainSegmentChecker = new Runnable() {
                @Override
                public void run() {
                    try {
                        final BlockId genesisBlockId = blockHeaderDatabaseManager.getBlockHeaderId(genesisBlock.getHash());
                        final BlockId block01BlockId = blockHeaderDatabaseManager.getBlockHeaderId(block01.getHash());
                        final BlockId block02BlockId = blockHeaderDatabaseManager.getBlockHeaderId(block02.getHash());
                        final BlockId invalidBlock01PrimeBlockId = blockHeaderDatabaseManager.getBlockHeaderId(invalidBlock01Prime.getHash());
                        final BlockId block03BlockId = blockHeaderDatabaseManager.getBlockHeaderId(block03.getHash());

                        final BlockchainSegmentId genesisBlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(genesisBlockId);
                        final BlockchainSegmentId block01BlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(block01BlockId);
                        final BlockchainSegmentId block02BlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(block02BlockId);
                        final BlockchainSegmentId block03BlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(block03BlockId);
                        final BlockchainSegmentId invalidBlock01PrimeBlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(invalidBlock01PrimeBlockId);

                        final boolean invalidBlockHasBeenStored = (invalidBlock01PrimeBlockId != null);

                        if (genesisBlockchainSegmentId == null) { return; }
                        if (block01BlockchainSegmentId == null) { return; }

                        // The genesis block, the first block of the valid chain, and the invalid block should all be on different blockchain segments.
                        if (invalidBlockHasBeenStored) {
                            Assert.assertNotEquals(genesisBlockchainSegmentId, block01BlockchainSegmentId);
                        }
                        else {
                            Assert.assertEquals(genesisBlockchainSegmentId, block01BlockchainSegmentId);
                        }

                        if (invalidBlock01PrimeBlockchainSegmentId == null) { return; }
                        Assert.assertNotEquals(genesisBlockchainSegmentId, invalidBlock01PrimeBlockchainSegmentId);
                        Assert.assertNotEquals(block01BlockchainSegmentId, invalidBlock01PrimeBlockchainSegmentId);

                        if (block02BlockchainSegmentId == null) { return; }
                        // The valid chain should all be on the same blockchain segment.
                        Assert.assertEquals(block01BlockchainSegmentId, block02BlockchainSegmentId);

                        if (block03BlockchainSegmentId == null) { return; }
                        Assert.assertEquals(block01BlockchainSegmentId, block03BlockchainSegmentId);
                    }
                    catch (final DatabaseException exception) {
                        throw new RuntimeException(exception);
                    }
                }
            };

            blockchainSegmentChecker.run();

            // Action
            blockProcessor.processBlock(block01);
            blockchainSegmentChecker.run(); // Assert

            blockProcessor.processBlock(block02);
            blockchainSegmentChecker.run(); // Assert

            blockProcessor.processBlock(invalidBlock01Prime); // Causes a fork at blockHeight=1
            blockchainSegmentChecker.run(); // Assert

            blockProcessor.processBlock(block03);
            blockchainSegmentChecker.run(); // Assert
        }
    }

    @Test
    public void should_maintain_correct_blockchain_segment_after_invalid_contentious_block_NO_CACHE() throws Exception {
        final MasterDatabaseManagerCache masterDatabaseManagerCache = new DisabledMasterDatabaseManagerCache();
        final DatabaseManagerCache localDatabaseManagerCache = new DisabledDatabaseManagerCache();

        _should_maintain_correct_blockchain_segment_after_invalid_contentious_block(localDatabaseManagerCache, masterDatabaseManagerCache);
    }

    @Test
    public void should_maintain_correct_blockchain_segment_after_invalid_contentious_block_READONLY_CACHE() throws Exception {
        final UtxoCount maxUtxoCount = NativeUnspentTransactionOutputCache.calculateMaxUtxoCountFromMemoryUsage(ByteUtil.Unit.GIGABYTES);
        final UnspentTransactionOutputCacheFactory unspentTransactionOutputCacheFactory = NativeUnspentTransactionOutputCache.createNativeUnspentTransactionOutputCacheFactory(maxUtxoCount);

        final MasterDatabaseManagerCache masterDatabaseManagerCache = new MasterDatabaseManagerCacheCore(unspentTransactionOutputCacheFactory);
        final DatabaseManagerCache databaseManagerCache = new ReadOnlyLocalDatabaseManagerCache(masterDatabaseManagerCache);

        _should_maintain_correct_blockchain_segment_after_invalid_contentious_block(databaseManagerCache, masterDatabaseManagerCache);
    }

    @Test
    public void should_maintain_correct_blockchain_segment_after_invalid_contentious_block_CACHE() throws Exception {
        final UtxoCount maxUtxoCount = NativeUnspentTransactionOutputCache.calculateMaxUtxoCountFromMemoryUsage(ByteUtil.Unit.GIGABYTES);
        final UnspentTransactionOutputCacheFactory unspentTransactionOutputCacheFactory = NativeUnspentTransactionOutputCache.createNativeUnspentTransactionOutputCacheFactory(maxUtxoCount);

        final MasterDatabaseManagerCache masterDatabaseManagerCache = new MasterDatabaseManagerCacheCore(unspentTransactionOutputCacheFactory);
        final DatabaseManagerCache databaseManagerCache = new LocalDatabaseManagerCache(masterDatabaseManagerCache);

        _should_maintain_correct_blockchain_segment_after_invalid_contentious_block(databaseManagerCache, masterDatabaseManagerCache);
    }
}
