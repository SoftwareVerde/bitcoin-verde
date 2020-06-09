package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.test.IntegrationTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

public class BlockDatabaseManagerPerformanceTests extends IntegrationTest {

    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception {
        super.after();
    }

    // TODO: Create a test that has a transaction whose transactionInputs spends a previousOutputTransactionHash of EMPTY_HASH and whose index is -1, but is not a coinbase transaction... (Probably fails...)

//    @Test
    public void should_store_giant_block_quickly() throws Exception {
//        // Setup
//        final MilliTimer setupTimer = new MilliTimer();
//        setupTimer.start();
//
//        try (
//            final MasterDatabaseManagerCache masterDatabaseManagerCache = new MasterDatabaseManagerCacheCore();
//            final LocalDatabaseManagerCache databaseManagerCache = new LocalDatabaseManagerCache(masterDatabaseManagerCache);
//            final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()
//        ) {
//            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
//            final BlockInflater blockInflater = new BlockInflater();
//
//            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
//
//            synchronized (BlockHeaderDatabaseManager.MUTEX) {
//                blockDatabaseManager.storeBlock(blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK)));
//                blockDatabaseManager.storeBlock(new MutableBlock(blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1))) {
//                    @Override
//                    public Sha256Hash getHash() {
//                        return Sha256Hash.fromHexString("00000000000000000008658CDABA34569F00748085DF3923CB5287E55A2FE27C");
//                    }
//
//                    @Override
//                    public Boolean isValid() {
//                        return true;
//                    }
//                });
//            }
//
//            final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(IoUtil.getResource("/blocks/000000000000000001C37467F0843DD9E09536C21938C5C20551191788A70541")));
//
//            _createRequiredTransactionInputs(block.getTransactions(), databaseConnection, databaseManagerCache);
//
//            setupTimer.stop();
//            System.out.println("Setup Duration: " + setupTimer.getMillisecondsElapsed() + "ms");
//
//            // Action
//            final MilliTimer storeTimer = new MilliTimer();
//            storeTimer.start();
//            final BlockId blockId;
//            synchronized (BlockHeaderDatabaseManager.MUTEX) {
//                blockId = blockDatabaseManager.storeBlock(block);
//            }
//            storeTimer.stop();
//            System.out.println("Store Duration: " + storeTimer.getMillisecondsElapsed() + "ms");
//
//            //  0.5-30ms each; 1231567ms total
//            //  80s - Implemented batch inserts
//            //  67s - Improved TxOutput Searching
//            //  53s - Batched TxOutput Searching
//            //  44s - Batched Address Inserting
//            // 174s - "Better" Batched Address Inserting (Inefficient Script::equals & Script::hashcode)
//            //  24s - Better Batched Address Inserting
//            //  26s - Added Duplicate-transaction detection
//            //  20s - Added Native UtxoCache
//
//            // Final Timing Results:
//            // Script Pattern Matching: 1ms
//            // Store Scripts: 1ms
//            // Store TransactionOutputs: 1ms
//            // Store LockingScripts: 61ms
//            // Store Transactions: 3ms
//            // Store TxOutputs: 62ms
//            // Store TxInputs: 4ms
//            // Stored 1 Transactions: 70ms
//            // Associated 1 Transactions: 1ms
//            // Setup Duration: 4615ms
//            // -----
//            // Store Addresses: 4276ms
//            // Script Pattern Matching: 69ms
//            // Store Scripts: 4035ms
//            // Store TransactionOutputs: 2097ms
//            // Store LockingScripts: 8380ms
//            // Store Transactions: 5471ms
//            // Store TxOutputs: 10560ms
//            // Store TxInputs: 8392ms
//            // Stored 95861 Transactions: 24423ms
//            // Associated 95861 Transactions: 1230ms
//            // Store Duration: 25847ms
//
//            // Assert
//            Assert.assertNotNull(blockId);
//
//            final Block reinflatedBlock = blockDatabaseManager.getBlock(blockId);
//            Assert.assertNotNull(reinflatedBlock);
//            Assert.assertEquals(block.getHash(), reinflatedBlock.getHash());
//        }
        Assert.fail();
    }

}
