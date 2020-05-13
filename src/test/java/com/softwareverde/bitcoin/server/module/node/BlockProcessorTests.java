package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.block.validator.BlockValidatorFactory;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputManager;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.OrphanedTransactionsCache;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.test.fake.FakeUnspentTransactionOutputSet;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.validator.MedianBlockTimeSet;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorFactory;
import com.softwareverde.bitcoin.wallet.PaymentAmount;
import com.softwareverde.bitcoin.wallet.Wallet;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.ListUtil;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.security.secp256k1.key.PrivateKey;
import com.softwareverde.util.BitcoinReflectionUtil;
import com.softwareverde.util.ReflectionUtil;
import com.softwareverde.util.type.time.SystemTime;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;

public class BlockProcessorTests extends IntegrationTest {
    protected static Long COINBASE_MATURITY = null;

    @Before
    public void setup() {
        _resetDatabase();

        BlockProcessorTests.COINBASE_MATURITY = TransactionValidator.COINBASE_MATURITY;
        BitcoinReflectionUtil.setStaticValue(TransactionValidator.class, "COINBASE_MATURITY", 0L);
    }

    @After
    public void tearDown() {
        if (BlockProcessorTests.COINBASE_MATURITY != null) {
            BitcoinReflectionUtil.setStaticValue(TransactionValidator.class, "COINBASE_MATURITY", 0L);
        }
    }

//    protected static void _should_maintain_correct_blockchain_segment_after_invalid_contentious_block(final DatabaseManagerCache databaseManagerCache, final MasterDatabaseManagerCache masterDatabaseManagerCache) throws Exception {
//        /*
//            This test emulates a found error in production on 2019-11-15 shortly after the hard fork.  While the HF did not cause the bug, it did cause
//            the bug to manifest when a 10+ old block was mined that was invalid with the new HF rules.
//
//            The cause of the bug involved a dirty read from the BlockchainSegment cache which was rolled back after the invalid block failed to process.
//
//            The test scenario executed in this test creates the following chain of blocks:
//
//                                      genesis (height=0, segment=1)
//                                        /\
//                                      /   \
//         (segment=3) invalidBlock01Prime   block01 (height=1, segment=2)
//                                            \
//                                             block02 (height=2, segment=2)
//                                             \
//                                              block03 (height=3, segment=2)
//
//            Where the insert-order is genesis -> block01 -> block02 -> invalidBlock01Prime -> block03
//         */
//
//        final DatabaseConnectionPool databaseConnectionPool = _database.getDatabaseConnectionPool();
//        final FullNodeDatabaseManagerFactory databaseManagerFactory = new FullNodeDatabaseManagerFactory(databaseConnectionPool, databaseManagerCache);
//
//        try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
//            // Setup
//            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
//
//            final BlockInflater blockInflater = new BlockInflater();
//            final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
//            synchronized (BlockHeaderDatabaseManager.MUTEX) {
//                blockDatabaseManager.insertBlock(genesisBlock);
//            }
//
//            final MasterInflater masterInflater = new CoreInflater();
//            final TransactionValidatorFactory transactionValidatorFactory = new TransactionValidatorFactory();
//
//            final MutableNetworkTime mutableNetworkTime = new MutableNetworkTime();
//
//            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
//            final MutableMedianBlockTime medianBlockTime = blockHeaderDatabaseManager.initializeMedianBlockTime();
//
//            final OrphanedTransactionsCache orphanedTransactionsCache = new OrphanedTransactionsCache(databaseManagerCache);
//
//            final BlockProcessor blockProcessor;
//            {
//                blockProcessor = new BlockProcessor(databaseManagerFactory, masterDatabaseManagerCache, masterInflater, transactionValidatorFactory, mutableNetworkTime, medianBlockTime, orphanedTransactionsCache, null);
//                blockProcessor.setMaxThreadCount(2);
//                blockProcessor.setTrustedBlockHeight(0L);
//            }
//
//            final Block block01 = blockInflater.fromBytes(ByteArray.fromHexString(BlockData.MainChain.BLOCK_1));
//            final Block block02 = blockInflater.fromBytes(ByteArray.fromHexString(BlockData.MainChain.BLOCK_2));
//            final Block block03 = blockInflater.fromBytes(ByteArray.fromHexString(BlockData.MainChain.BLOCK_3));
//            final Block invalidBlock01Prime = blockInflater.fromBytes(ByteArray.fromHexString("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D619000000000073387C6C752B492D7D6DA0CA48715EE10394683D4421B602E80B754657B2E0A79130D05DFFFF001DE339AB7E0201000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D0104FFFFFFFF0100F2052A0100000043410496B538E853519C726A2C91E61EC11600AE1390813A627C66FB8BE7947BE63C52DA7589379515D4E0A604F8141781E62294721166BF621E73A82CBF2342C858EEAC0000000002000000013BA3EDFD7A7B12B27AC72C3E67768F617FC81BC3888A51323A9FB8AA4B1E5E4A0000000000FFFFFFFF0100F2052A0100000043410496B538E853519C726A2C91E61EC11600AE1390813A627C66FB8BE7947BE63C52DA7589379515D4E0A604F8141781E62294721166BF621E73A82CBF2342C858EEAC00000000"));
//
//            final Runnable blockchainSegmentChecker = new Runnable() {
//                @Override
//                public void run() {
//                    try {
//                        final BlockId genesisBlockId = blockHeaderDatabaseManager.getBlockHeaderId(genesisBlock.getHash());
//                        final BlockId block01BlockId = blockHeaderDatabaseManager.getBlockHeaderId(block01.getHash());
//                        final BlockId block02BlockId = blockHeaderDatabaseManager.getBlockHeaderId(block02.getHash());
//                        final BlockId invalidBlock01PrimeBlockId = blockHeaderDatabaseManager.getBlockHeaderId(invalidBlock01Prime.getHash());
//                        final BlockId block03BlockId = blockHeaderDatabaseManager.getBlockHeaderId(block03.getHash());
//
//                        final BlockchainSegmentId genesisBlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(genesisBlockId);
//                        final BlockchainSegmentId block01BlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(block01BlockId);
//                        final BlockchainSegmentId block02BlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(block02BlockId);
//                        final BlockchainSegmentId block03BlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(block03BlockId);
//                        final BlockchainSegmentId invalidBlock01PrimeBlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(invalidBlock01PrimeBlockId);
//
//                        final boolean invalidBlockHasBeenStored = (invalidBlock01PrimeBlockId != null);
//
//                        if (genesisBlockchainSegmentId == null) { return; }
//                        if (block01BlockchainSegmentId == null) { return; }
//
//                        // The genesis block, the first block of the valid chain, and the invalid block should all be on different blockchain segments.
//                        if (invalidBlockHasBeenStored) {
//                            Assert.assertNotEquals(genesisBlockchainSegmentId, block01BlockchainSegmentId);
//                        }
//                        else {
//                            Assert.assertEquals(genesisBlockchainSegmentId, block01BlockchainSegmentId);
//                        }
//
//                        if (invalidBlock01PrimeBlockchainSegmentId == null) { return; }
//                        Assert.assertNotEquals(genesisBlockchainSegmentId, invalidBlock01PrimeBlockchainSegmentId);
//                        Assert.assertNotEquals(block01BlockchainSegmentId, invalidBlock01PrimeBlockchainSegmentId);
//
//                        if (block02BlockchainSegmentId == null) { return; }
//                        // The valid chain should all be on the same blockchain segment.
//                        Assert.assertEquals(block01BlockchainSegmentId, block02BlockchainSegmentId);
//
//                        if (block03BlockchainSegmentId == null) { return; }
//                        Assert.assertEquals(block01BlockchainSegmentId, block03BlockchainSegmentId);
//                    }
//                    catch (final DatabaseException exception) {
//                        throw new RuntimeException(exception);
//                    }
//                }
//            };
//
//            blockchainSegmentChecker.run();
//
//            // Action
//            blockProcessor.processBlock(block01);
//            blockchainSegmentChecker.run(); // Assert
//
//            blockProcessor.processBlock(block02);
//            blockchainSegmentChecker.run(); // Assert
//
//            blockProcessor.processBlock(invalidBlock01Prime); // Causes a fork at blockHeight=1
//            blockchainSegmentChecker.run(); // Assert
//
//            blockProcessor.processBlock(block03);
//            blockchainSegmentChecker.run(); // Assert
//        }
//    }

    class TestHarness {
        public final BlockInflater blockInflater = _masterInflater.getBlockInflater();
        public final MutableNetworkTime networkTime = new MutableNetworkTime();
        public final MutableMedianBlockTime medianBlockTime = new MutableMedianBlockTime();
        public final OrphanedTransactionsCache orphanedTransactionsCache = new OrphanedTransactionsCache();
        public final FakeUnspentTransactionOutputSet unspentTransactionOutputSet = new FakeUnspentTransactionOutputSet();
        public final HashMap<Sha256Hash, MedianBlockTime> medianBlockTimes = new HashMap<Sha256Hash, MedianBlockTime>();

        public final TransactionValidatorFactory transactionValidatorFactory = new TransactionValidatorFactory(this.networkTime, this.medianBlockTime, new MedianBlockTimeSet() {
            @Override
            public MedianBlockTime getMedianBlockTime(final Sha256Hash blockHash) {
                return TestHarness.this.medianBlockTimes.get(blockHash);
            }
        });
        public final BlockValidatorFactory blockValidatorFactory = new BlockValidatorFactory(this.transactionValidatorFactory, this.networkTime, this.medianBlockTime);

        public final BlockProcessor blockProcessor = new BlockProcessor(_fullNodeDatabaseManagerFactory, _masterInflater, this.blockValidatorFactory, this.medianBlockTime, this.orphanedTransactionsCache, _blockStore, _synchronizationStatus);

        public TestHarness() {
            blockProcessor.setMaxThreadCount(1);
            blockProcessor.setTrustedBlockHeight(BlockValidator.DO_NOT_TRUST_BLOCKS);
        }

        public Long processBlock(final Block block) {
            return this.blockProcessor.processBlock(block, this.unspentTransactionOutputSet);
        }

        public Block inflateBlock(final String blockData) {
            return this.blockInflater.fromBytes(ByteArray.fromHexString(blockData));
        }

        public UnspentTransactionOutputManager newUnspentTransactionOutputManager(final FullNodeDatabaseManager databaseManager) {
            return new UnspentTransactionOutputManager(databaseManager, _databaseConnectionFactory, Long.MAX_VALUE);
        }
    }

    @Test
    public void should_maintain_correct_blockchain_segment_after_invalid_contentious_block() throws Exception {
        Assert.fail();
    }

    @Test
    public void should_process_genesis_block() throws Exception {
        /**
         * NOTE: On the NodeModule, the blockProcessor doesn't process the genesis block, instead it is processed differently by the BlockchainBuilder...
         */

        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final TestHarness harness = new TestHarness();
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();

            final Block genesisBlock = harness.inflateBlock(BlockData.MainChain.GENESIS_BLOCK);

            final TransactionOutputIdentifier transactionOutputIdentifier;
            {
                final List<Transaction> transactions = genesisBlock.getTransactions();
                final Transaction transaction = transactions.get(0);
                transactionOutputIdentifier = new TransactionOutputIdentifier(transaction.getHash(), 0);
            }

            // Action
            final Long blockHeight = harness.processBlock(genesisBlock);
            final TransactionOutput transactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(transactionOutputIdentifier);

            // Assert
            Assert.assertEquals(Long.valueOf(0L), blockHeight);
            Assert.assertNull(transactionOutput); // Outputs created by the genesis are not spendable...
        }
    }

    @Test
    public void should_process_blocks_with_utxos() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final TestHarness harness = new TestHarness();
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();

            final Block genesisBlock = harness.inflateBlock(BlockData.MainChain.GENESIS_BLOCK);
            final Block block01 = harness.inflateBlock(BlockData.MainChain.BLOCK_1);

            final TransactionOutput expectedTransactionOutput;
            final TransactionOutputIdentifier transactionOutputIdentifier;
            {
                final List<Transaction> transactions = block01.getTransactions();
                final Transaction transaction = transactions.get(0);
                final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();

                expectedTransactionOutput = transactionOutputs.get(0);
                transactionOutputIdentifier = new TransactionOutputIdentifier(transaction.getHash(), 0);
            }

            // Action
            harness.processBlock(genesisBlock);
            final Long blockHeight = harness.processBlock(block01);
            final TransactionOutput transactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(transactionOutputIdentifier);

            // Assert
            Assert.assertEquals(Long.valueOf(1L), blockHeight);
            Assert.assertEquals(expectedTransactionOutput, transactionOutput);
        }
    }

    @Test
    public void should_handle_reorg_fork() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final TestHarness harness = new TestHarness();
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();

            final Block genesisBlock = harness.inflateBlock(BlockData.MainChain.GENESIS_BLOCK);
            final Block mainChainBlock01 = harness.inflateBlock(BlockData.MainChain.BLOCK_1);
            final Block mainChainBlock02 = harness.inflateBlock(BlockData.MainChain.BLOCK_2);
            final Block forkChainBlock01 = harness.inflateBlock(BlockData.ForkChain2.BLOCK_1);

            final TransactionOutputIdentifier invalidTransactionOutputIdentifier;
            {
                final List<Transaction> transactions = forkChainBlock01.getTransactions();
                final Transaction transaction = transactions.get(0);
                invalidTransactionOutputIdentifier = new TransactionOutputIdentifier(transaction.getHash(), 0);
            }

            final Transaction transaction;
            { // Inflate transaction that spends the coinbase of the ForkChain2.BLOCK_1...
                final TransactionInflater transactionInflater = _masterInflater.getTransactionInflater();
                transaction = transactionInflater.fromBytes(ByteArray.fromHexString("0200000001F2857FE43B7FE710900C50F38DEFFDEF0304D05F0911BBA7CAC9859BD0797D0E000000008B483045022100AFC23C6CB284C4897BA7EFAF867B45D0A80D60EA13B4EE97394A66A9A9DCE863022049B91D579050DC5BB3CB5767A3D0A0AB2A8E7D77B0A7C4B96C7F2EDEAD0DDB78414104369319023063307A8209C518C0E07CC27AA2502113907BEECA66DEFA0669DEA00D995BEC5AB5964368769C4A772F3B04C9DFA002A14BE8B27BD0E3A57CEBFDA9FFFFFFFF0100F2052A010000001976A91410DB8BE45C9035835DD8B31E811143166D9907EA88AC00000000"));
            }

            // Action
            harness.processBlock(genesisBlock);

            final Long blockHeightStep1 = harness.processBlock(forkChainBlock01);
            final TransactionId transactionId = transactionDatabaseManager.storeUnconfirmedTransaction(transaction);
            transactionDatabaseManager.addToUnconfirmedTransactions(transactionId);

            final Long blockHeightStep2 = harness.processBlock(mainChainBlock01);
            final Long blockHeightStep3 = harness.processBlock(mainChainBlock02);

            // Assert
            Assert.assertEquals(Long.valueOf(2L), blockHeightStep3);

            // The output generated by the old chain should no longer be a UTXO...
            final TransactionOutput oldTransactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(invalidTransactionOutputIdentifier);
            Assert.assertNull(oldTransactionOutput);

            // The transaction spending the fork chain's UTXO should no longer be in the mempool...
            final List<TransactionId> unconfirmedTransactionIds = transactionDatabaseManager.getUnconfirmedTransactionIds();
            Assert.assertFalse(unconfirmedTransactionIds.contains(transactionId));
        }
    }

    protected static Boolean utxoExistsInCommittedUtxoSet(final Transaction transaction, final DatabaseConnection databaseConnection) throws DatabaseException {
        final Integer committedUtxoCount = databaseConnection.query(
            new Query("SELECT 1 FROM committed_unspent_transaction_outputs WHERE transaction_hash = ? AND `index` = 0 AND is_spent = 0")
                .setParameter(transaction.getHash())
        ).size();
        return (committedUtxoCount != 0);
    }

    @Test
    public void should_handle_reorg_fork_with_utxo_committed() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final TestHarness harness = new TestHarness();
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();

            final Block genesisBlock = harness.inflateBlock(BlockData.MainChain.GENESIS_BLOCK);
            final Block mainChainBlock01 = harness.inflateBlock(BlockData.MainChain.BLOCK_1);
            final Block mainChainBlock02 = harness.inflateBlock(BlockData.MainChain.BLOCK_2);
            final Block forkChainBlock01 = harness.inflateBlock(BlockData.ForkChain2.BLOCK_1);

            final TransactionOutputIdentifier invalidTransactionOutputIdentifier;
            {
                final List<Transaction> transactions = forkChainBlock01.getTransactions();
                final Transaction transaction = transactions.get(0);
                invalidTransactionOutputIdentifier = new TransactionOutputIdentifier(transaction.getHash(), 0);
            }

            final Transaction forkChainCoinbaseTransaction = forkChainBlock01.getCoinbaseTransaction();

            final Transaction transaction;
            { // Inflate transaction that spends the coinbase of the ForkChain2.BLOCK_1...
                final TransactionInflater transactionInflater = _masterInflater.getTransactionInflater();
                transaction = transactionInflater.fromBytes(ByteArray.fromHexString("0200000001F2857FE43B7FE710900C50F38DEFFDEF0304D05F0911BBA7CAC9859BD0797D0E000000008B483045022100AFC23C6CB284C4897BA7EFAF867B45D0A80D60EA13B4EE97394A66A9A9DCE863022049B91D579050DC5BB3CB5767A3D0A0AB2A8E7D77B0A7C4B96C7F2EDEAD0DDB78414104369319023063307A8209C518C0E07CC27AA2502113907BEECA66DEFA0669DEA00D995BEC5AB5964368769C4A772F3B04C9DFA002A14BE8B27BD0E3A57CEBFDA9FFFFFFFF0100F2052A010000001976A91410DB8BE45C9035835DD8B31E811143166D9907EA88AC00000000"));
            }

            // Action
            harness.processBlock(genesisBlock);

            final Long blockHeightStep1 = harness.processBlock(forkChainBlock01);
            final TransactionId transactionId = transactionDatabaseManager.storeUnconfirmedTransaction(transaction);
            transactionDatabaseManager.addToUnconfirmedTransactions(transactionId);
            unspentTransactionOutputDatabaseManager.commitUnspentTransactionOutputs(_databaseConnectionFactory); // Commit the UTXO set with outputs that will then be invalidated during a reorg...
            Assert.assertTrue(BlockProcessorTests.utxoExistsInCommittedUtxoSet(forkChainCoinbaseTransaction, databaseConnection)); // Ensure the UTXO was actually committed...

            final Long blockHeightStep2 = harness.processBlock(mainChainBlock01);
            final Long blockHeightStep3 = harness.processBlock(mainChainBlock02);

            // Assert
            Assert.assertEquals(Long.valueOf(2L), blockHeightStep3);

            // The output generated by the old chain should no longer be a UTXO...
            final TransactionOutput oldTransactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(invalidTransactionOutputIdentifier);
            Assert.assertNull(oldTransactionOutput);

            // Ensure the invalid UTXO isn't left within the on-disk UTXO set...
            Assert.assertFalse(BlockProcessorTests.utxoExistsInCommittedUtxoSet(forkChainCoinbaseTransaction, databaseConnection));
            Assert.assertFalse(BlockProcessorTests.utxoExistsInCommittedUtxoSet(transaction, databaseConnection));

            // The transaction spending the fork chain's UTXO should no longer be in the mempool...
            final List<TransactionId> unconfirmedTransactionIds = transactionDatabaseManager.getUnconfirmedTransactionIds();
            Assert.assertFalse(unconfirmedTransactionIds.contains(transactionId));
        }
    }

    @Test
    public void should_handle_contentious_reorg_fork_with_shared_utxos() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final AddressInflater addressInflater = _masterInflater.getAddressInflater();

            final TestHarness harness = new TestHarness();
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();

            final Block genesisBlock = harness.inflateBlock(BlockData.MainChain.GENESIS_BLOCK);
            final Block forkChain2Block01 = harness.inflateBlock(BlockData.ForkChain2.BLOCK_1);
            final Block forkChain2Block02 = harness.inflateBlock(BlockData.ForkChain2.BLOCK_2);
            final Block forkChain2Block03 = harness.inflateBlock(BlockData.ForkChain2.BLOCK_3);
            // final Block forkChain2Block04 = harness.inflateBlock(BlockData.ForkChain2.BLOCK_4);
            final Block forkChain4Block03 = harness.inflateBlock(BlockData.ForkChain4.BLOCK_3);

            System.out.println(genesisBlock.getHash()); // 000000000019D6689C085AE165831E934FF763AE46A2A6C172B3F1B60A8CE26F
            System.out.println(forkChain2Block01.getHash()); // 0000000001BE52D653305F7D80ED373837E61CC26AE586AFD343A3C2E64E64A2
            System.out.println(forkChain2Block02.getHash()); // 00000000314E669144E0781C432EB33F2079834D406E46393291E94199F433EE
            System.out.println(forkChain2Block03.getHash()); // 0000000092764BB13AAE7477F4AB90E2AC33D85DCECF9F92F8FC679FBF5BA842
            // System.out.println(forkChain2Block04.getHash());
            System.out.println(forkChain4Block03.getHash()); // 000000000B869A3A1B5A52698A0B9479F0673ECD53994B94D73CDE12A3A18828

            // Action
            harness.processBlock(genesisBlock);

            final Long blockHeightStep1 = harness.processBlock(forkChain2Block01);
            final Long blockHeightStep2 = harness.processBlock(forkChain2Block02);
            // final Long blockHeightStep3 = harness.processBlock(forkChain4Block03);
            final Long blockHeightStep4 = harness.processBlock(forkChain2Block03);
            // final Long blockHeightStep5 = harness.processBlock(forkChain2Block04);

            // Assert
            Assert.assertEquals(Long.valueOf(1L), blockHeightStep1);
            Assert.assertEquals(Long.valueOf(2L), blockHeightStep2);
            // Assert.assertEquals(Long.valueOf(3L), blockHeightStep3);
            Assert.assertEquals(Long.valueOf(3L), blockHeightStep4);
            // Assert.assertEquals(Long.valueOf(4L), blockHeightStep5);
        }
    }

    @Test
    public void makeBlockPrototype() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final AddressInflater addressInflater = _masterInflater.getAddressInflater();

            final TestHarness harness = new TestHarness();
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();

            final Block genesisBlock = harness.inflateBlock(BlockData.MainChain.GENESIS_BLOCK);
            final Block forkChain2Block01 = harness.inflateBlock(BlockData.ForkChain2.BLOCK_1);
            final Block forkChain2Block02 = harness.inflateBlock(BlockData.ForkChain2.BLOCK_2);
            // final Block forkChain2Block03 = harness.inflateBlock(BlockData.ForkChain2.BLOCK_3);
            // final Block forkChain4Block03 = harness.inflateBlock(BlockData.ForkChain4.BLOCK_3);

            final Block previousBlock = forkChain2Block02;

            final PrivateKey privateKey = PrivateKey.createNewKey(); // PrivateKey.fromHexString("697D9CCCD7A09A31ED41C1D1BFF35E2481098FB03B4E73FAB7D4C15CF01FADCC");
            System.out.println(privateKey);

            final Transaction transaction;
            {
                final Address destinationAddress = addressInflater.compressedFromPrivateKey(privateKey);
                final Wallet wallet = new Wallet();
                ReflectionUtil.setValue(wallet, "_createBitcoinCashSignature", false);
                wallet.setSatoshisPerByteFee(0D);
                wallet.addPrivateKey(PrivateKey.fromHexString("697D9CCCD7A09A31ED41C1D1BFF35E2481098FB03B4E73FAB7D4C15CF01FADCC"));
                wallet.addTransaction(previousBlock.getCoinbaseTransaction());
                final List<PaymentAmount> paymentAmounts = ListUtil.newMutableList(new PaymentAmount(destinationAddress, (50L * Transaction.SATOSHIS_PER_BITCOIN)));
                transaction = wallet.createTransaction(paymentAmounts, destinationAddress);
            }

            final TransactionInflater transactionInflater = _masterInflater.getTransactionInflater();
            final SystemTime systemTime = new SystemTime();
            final MutableBlock mutableBlock = new MutableBlock();
            mutableBlock.setVersion(BlockHeader.VERSION);
            mutableBlock.setPreviousBlockHash(previousBlock.getHash());
            mutableBlock.setDifficulty(previousBlock.getDifficulty());
            mutableBlock.setTimestamp(systemTime.getCurrentTimeInSeconds());
            mutableBlock.setNonce(0L);
            mutableBlock.addTransaction(
                transactionInflater.createCoinbaseTransaction(
                    3L,
                    privateKey.toString(),
                    addressInflater.compressedFromPrivateKey(privateKey),
                    (50L * Transaction.SATOSHIS_PER_BITCOIN)
                )
            );
            mutableBlock.addTransaction(transaction);

            final BlockDeflater blockDeflater = _masterInflater.getBlockDeflater();
            System.out.println(blockDeflater.toBytes(mutableBlock));
        }
    }
}
