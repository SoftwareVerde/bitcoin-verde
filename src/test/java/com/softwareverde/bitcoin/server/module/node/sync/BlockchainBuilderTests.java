package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BlockchainBuilderTests extends IntegrationTest {
//    static class FakeBlockDownloadRequester implements BlockDownloadRequester {
//        @Override
//        public void requestBlock(final BlockHeader blockHeader) { }
//
//        @Override
//        public void requestBlock(final Sha256Hash blockHash, final Long priority) { }
//
//        @Override
//        public void requestBlock(final Sha256Hash blockHash) { }
//    }

    @Before @Override
    public void before() {
        super.before();
    }

    @After @Override
    public void after() {
        super.after();
    }

    @Test
    public void should_synchronize_pending_blocks() throws Exception {
//        // Setup
//        try (final DatabaseConnectionFactory databaseConnectionFactory = _database.getDatabaseConnectionFactory();
//             final MasterDatabaseManagerCache masterCache = new MasterDatabaseManagerCacheCore();
//             final DatabaseManagerCache databaseCache = new ReadOnlyLocalDatabaseManagerCache(masterCache);
//             final DatabaseConnection databaseConnection = databaseConnectionFactory.newConnection();
//             final FullNodeDatabaseManager databaseManager = new FullNodeDatabaseManager(databaseConnection, databaseCache);
//        ) {
//            final FullNodeDatabaseManagerFactory databaseManagerFactory = new FullNodeDatabaseManagerFactory(databaseConnectionFactory, databaseCache);
//
//            final BlockInflater blockInflater = new BlockInflater();
//            final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
//
//            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
//            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
//
//            synchronized (BlockHeaderDatabaseManager.MUTEX) {
//                blockDatabaseManager.storeBlock(genesisBlock);
//            }
//
//            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();
//            for (final String blockData : new String[] { BlockData.MainChain.BLOCK_1, BlockData.MainChain.BLOCK_2, BlockData.MainChain.BLOCK_3, BlockData.MainChain.BLOCK_4, BlockData.MainChain.BLOCK_5 }) {
//                final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
//                pendingBlockDatabaseManager.storeBlock(block);
//            }
//
//            final BlockchainBuilder blockchainBuilder;
//            {
//                final NetworkTime networkTime = new MutableNetworkTime();
//                final MutableMedianBlockTime medianBlockTime = new MutableMedianBlockTime();
//                final BitcoinNodeManager nodeManager = new FakeBitcoinNodeManager();
//
//                final OrphanedTransactionsCache orphanedTransactionsCache = new OrphanedTransactionsCache(databaseCache);
//
//                final TransactionValidatorFactory transactionValidatorFactory = new TransactionValidatorFactory();
//                final BlockProcessor blockProcessor = new BlockProcessor(databaseManagerFactory, masterCache, transactionValidatorFactory, networkTime, medianBlockTime, orphanedTransactionsCache, null);
//                final SleepyService.StatusMonitor blockDownloaderStatusMonitor = new SleepyService.StatusMonitor() {
//                    @Override
//                    public SleepyService.Status getStatus() {
//                        return SleepyService.Status.ACTIVE;
//                    }
//                };
//                blockchainBuilder = new BlockchainBuilder(nodeManager, databaseManagerFactory, blockProcessor, blockDownloaderStatusMonitor, new FakeBlockDownloadRequester(), _threadPool);
//            }
//
//            Assert.assertTrue(blockchainBuilder._hasGenesisBlock);
//
//            // Action
//            blockchainBuilder.start();
//            Thread.sleep(1000L);
//            blockchainBuilder.stop();
//
//            // Assert
//            final BlockchainSegmentId blockchainSegmentId = BlockchainSegmentId.wrap(1L);
//            Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, 1L));
//            Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, 2L));
//            Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, 3L));
//            Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, 4L));
//            Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, 5L));
//        }
        Assert.fail();
    }


    @Test
    public void should_not_be_invalid_if_spent_on_different_chain() throws Exception {
//        // Setup
//        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
//            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
//            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
//
//            final BlockInflater blockInflater = new BlockInflater();
//            final AddressInflater addressInflater = new AddressInflater();
//            final TransactionSigner transactionSigner = new TransactionSigner();
//            final TransactionValidatorFactory transactionValidatorFactory = new TransactionValidatorFactory();
//            final TransactionValidator transactionValidator = transactionValidatorFactory.newTransactionValidator(databaseManager, NetworkTime.MAX_VALUE, MedianBlockTime.MAX_VALUE);
//            final BlockValidator blockValidator = new BlockValidator(_readUncomittedDatabaseManagerFactory, transactionValidatorFactory, NetworkTime.MAX_VALUE, new FakeMedianBlockTime());
//            final TransactionOutputRepository transactionOutputRepository = new DatabaseTransactionOutputRepository(databaseManager);
//
//            Sha256Hash lastBlockHash = null;
//            Block lastBlock = null;
//            BlockId lastBlockId = null;
//            for (final String blockData : new String[] { BlockData.MainChain.GENESIS_BLOCK, BlockData.MainChain.BLOCK_1, BlockData.MainChain.BLOCK_2 }) {
//                final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
//                synchronized (BlockHeaderDatabaseManager.MUTEX) {
//                    lastBlockId = blockDatabaseManager.storeBlock(block);
//                }
//                lastBlock = block;
//                lastBlockHash = block.getHash();
//            }
//            Assert.assertNotNull(lastBlock);
//            Assert.assertNotNull(lastBlockId);
//            Assert.assertNotNull(lastBlockHash);
//
//            final PrivateKey privateKey = PrivateKey.createNewKey();
//
//            final Transaction spendableCoinbase;
//            final MutableBlock blockWithSpendableCoinbase = new MutableBlock() {
//                @Override
//                public Boolean isValid() { return true; } // Disables basic header validation...
//            };
//
//            {
//                blockWithSpendableCoinbase.setDifficulty(lastBlock.getDifficulty());
//                blockWithSpendableCoinbase.setNonce(lastBlock.getNonce());
//                blockWithSpendableCoinbase.setTimestamp(lastBlock.getTimestamp());
//                blockWithSpendableCoinbase.setVersion(lastBlock.getVersion());
//
//                // Create a transaction that will be spent in our signed transaction.
//                //  This transaction will create an output that can be spent by our private key.
//                spendableCoinbase = TransactionValidatorTests._createTransactionContaining(
//                    TransactionValidatorTests._createCoinbaseTransactionInput(),
//                    TransactionValidatorTests._createTransactionOutput(addressInflater.uncompressedFromPrivateKey(privateKey), 50L * Transaction.SATOSHIS_PER_BITCOIN)
//                );
//
//                blockWithSpendableCoinbase.addTransaction(spendableCoinbase);
//
//                synchronized (BlockHeaderDatabaseManager.MUTEX) {
//                    blockWithSpendableCoinbase.setPreviousBlockHash(lastBlockHash);
//                    final BlockId blockId = blockDatabaseManager.storeBlock(blockWithSpendableCoinbase); // Block3
//                    lastBlockHash = blockWithSpendableCoinbase.getHash();
//
//                    final Boolean blockIsValid = blockValidator.validateBlock(blockId, blockWithSpendableCoinbase).isValid;
//                    Assert.assertTrue(blockIsValid);
//                }
//            }
//
//            final Transaction signedTransactionSpendingDuplicateCoinbase;
//            {
//                final MutableTransaction unsignedTransaction = TransactionValidatorTests._createTransactionContaining(
//                        TransactionValidatorTests._createTransactionInputThatSpendsTransaction(spendableCoinbase),
//                        TransactionValidatorTests._createTransactionOutput(addressInflater.uncompressedFromBase58Check("1HrXm9WZF7LBm3HCwCBgVS3siDbk5DYCuW"), 50L * Transaction.SATOSHIS_PER_BITCOIN)
//                );
//
//                // Sign the transaction..
//                final SignatureContextGenerator signatureContextGenerator = new SignatureContextGenerator(transactionOutputRepository);
//                final SignatureContext signatureContext = signatureContextGenerator.createContextForEntireTransaction(unsignedTransaction, false);
//                signedTransactionSpendingDuplicateCoinbase = transactionSigner.signTransaction(signatureContext, privateKey);
//
//                transactionDatabaseManager.storeTransaction(signedTransactionSpendingDuplicateCoinbase);
//            }
//
//            { // Ensure the transaction would normally be valid on its own...
//                final Boolean isValid = transactionValidator.validateTransaction(BlockchainSegmentId.wrap(1L), TransactionValidatorTests.calculateBlockHeight(databaseManager), signedTransactionSpendingDuplicateCoinbase, false);
//                Assert.assertTrue(isValid);
//            }
//
//            { // Spend the coinbase...
//                final MutableBlock mutableBlock = new MutableBlock(blockWithSpendableCoinbase) {
//                    @Override
//                    public Boolean isValid() { return true; }
//                };
//                mutableBlock.clearTransactions();
//
//                final Transaction regularCoinbaseTransaction = TransactionValidatorTests._createTransactionContaining(
//                        TransactionValidatorTests._createCoinbaseTransactionInput(),
//                        TransactionValidatorTests._createTransactionOutput(addressInflater.uncompressedFromBase58Check("13usM2ns3f466LP65EY1h8hnTBLFiJV6rD"), 50L * Transaction.SATOSHIS_PER_BITCOIN)
//                );
//
//                mutableBlock.addTransaction(regularCoinbaseTransaction);
//                mutableBlock.addTransaction(signedTransactionSpendingDuplicateCoinbase);
//
//                synchronized (BlockHeaderDatabaseManager.MUTEX) {
//                    mutableBlock.setPreviousBlockHash(lastBlockHash);
//                    final BlockId blockId = blockDatabaseManager.storeBlock(mutableBlock); // Block4
//                    lastBlockHash = mutableBlock.getHash();
//
//                    final Boolean blockIsValid = blockValidator.validateBlock(blockId, mutableBlock).isValid;
//                    Assert.assertTrue(blockIsValid);
//                }
//            }
//
//            { // Spend the coinbase on a separate chain...
//                final MutableBlock mutableBlock = new MutableBlock(blockWithSpendableCoinbase) {
//                    @Override
//                    public Boolean isValid() { return true; }
//                };
//                mutableBlock.clearTransactions();
//
//                final Transaction regularCoinbaseTransaction = TransactionValidatorTests._createTransactionContaining(
//                    TransactionValidatorTests._createCoinbaseTransactionInput(),
//                    TransactionValidatorTests._createTransactionOutput(addressInflater.uncompressedFromBase58Check("1DgiazmkoTEdvTa6ErdzrqvmnenGS11RU2"), 50L * Transaction.SATOSHIS_PER_BITCOIN)
//                );
//
//                mutableBlock.addTransaction(regularCoinbaseTransaction);
//                mutableBlock.addTransaction(signedTransactionSpendingDuplicateCoinbase);
//
//                synchronized (BlockHeaderDatabaseManager.MUTEX) {
//                    mutableBlock.setPreviousBlockHash(blockWithSpendableCoinbase.getHash());
//                    final BlockId blockId = blockDatabaseManager.storeBlock(mutableBlock); // Block4Prime
//                    lastBlockHash = mutableBlock.getHash();
//
//                    final Boolean blockIsValid = blockValidator.validateBlock(blockId, mutableBlock).isValid;
//                    Assert.assertTrue(blockIsValid);
//                }
//            }
//        }
        Assert.fail();
    }

    @Test
    public void should_not_validate_transaction_when_transaction_output_is_only_found_on_separate_fork() throws Exception {
//        // Setup
//        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
//
//            final BlockInflater blockInflater = new BlockInflater();
//            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
//
//            final TransactionValidatorFactory transactionValidatorFactory = new TransactionValidatorFactory();
//            final BlockValidator blockValidator = new BlockValidator(_readUncomittedDatabaseManagerFactory, transactionValidatorFactory, NetworkTime.MAX_VALUE, new FakeMedianBlockTime());
//
//            final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
//            final Block block1 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));
//            final Block block2 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_2));
//            final Block block3 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_3));
//            final Block block4 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_4));
//            final Block block5 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_5));
//
//            final Block customBlock6 = blockInflater.fromBytes(HexUtil.hexStringToByteArray("01000000FC33F596F822A0A1951FFDBF2A897B095636AD871707BF5D3162729B00000000E04DAA8565BEFFCEF1949AC5582B7DF359A10A2138409503A1B8B8D3C7355D539CC56649FFFF001D4A0CDDD801010000000100000000000000000000000000000000000000000000000000000000000000000000000020184D696E65642076696120426974636F696E2D56657264652E06313134353332FFFFFFFF0100F2052A010000001976A914F1A626E143DCC5E75E8E6BE3F2CE1CF3108FB53D88AC00000000"));
//            final Block invalidBlock6 = blockInflater.fromBytes(HexUtil.hexStringToByteArray("01000000FC33F596F822A0A1951FFDBF2A897B095636AD871707BF5D3162729B00000000CA62264F9C5F8C91919DEEB07AEBAEA6D7699B370027EBA290C2154C6344476EA730975BFFFF001D2070BB0202010000000100000000000000000000000000000000000000000000000000000000000000000000000019184D696E65642076696120426974636F696E2D56657264652EFFFFFFFF0100F2052A010000001976A914F1A626E143DCC5E75E8E6BE3F2CE1CF3108FB53D88AC000000000100000001E04DAA8565BEFFCEF1949AC5582B7DF359A10A2138409503A1B8B8D3C7355D53000000008A47304402202876A32EBDA4BB8D29F5E7596CF0B0F4E9C97D3BDF4C15BE4F13CA64692B002802201F3C9A1B2474907CAE9C505CDD96C8B2F7B7277098FBBA2ED5BAE4BC7C45A4750141046C1D8C923D8ADFCEA711BE28A9BF7E2981632AAC789AEF95D7402B9225784AD93661700AB5474EFFDD7D5BEA6100904D3F1B3BE2017E2A18971DD8904B522020FFFFFFFF0100F2052A010000001976A914F1A626E143DCC5E75E8E6BE3F2CE1CF3108FB53D88AC00000000"));
//
//            // NOTE: invalidBlock6 attempts to spend an output that only exists within customBlock6. Since the TransactionOutput does not exist on its chain, this block is invalid.
//            Assert.assertEquals(customBlock6.getTransactions().get(0).getHash(), invalidBlock6.getTransactions().get(1).getTransactionInputs().get(0).getPreviousOutputTransactionHash());
//
//            final BlockId blockId;
//            synchronized (BlockHeaderDatabaseManager.MUTEX) {
//                for (final Block block : new Block[]{genesisBlock, block1, block2, block3, block4, block5}) {
//                    blockDatabaseManager.storeBlock(block);
//                }
//
//                blockDatabaseManager.storeBlock(customBlock6);
//                blockId = blockDatabaseManager.storeBlock(invalidBlock6);
//            }
//
//            // Action
//            final Boolean block2PrimeIsValid = blockValidator.validateBlock(blockId, invalidBlock6).isValid;
//
//            // Assert
//            Assert.assertFalse(block2PrimeIsValid);
//        }
        Assert.fail();
    }

    @Test
    public void block_should_be_invalid_if_its_input_only_exists_within_a_different_chain() throws Exception {
//        // Setup
//
//        /*
//            // Given the following forking situation...
//
//            [2']                    // [2'] attempts to spend {UTXO-A}, [2'] does NOT include {UTXO-A}.
//             |
//            [1']  [1'']             // [1''] includes {UTXO-A}, [1'] does NOT include {UTXO-A}.
//             |     |
//            [0]----+
//             |
//
//            //
//        */
//
//        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
//            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
//            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
//            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
//
//            final BlockInflater blockInflater = new BlockInflater();
//            final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
//
//            final Block block1Prime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain2.BLOCK_1));
//            Assert.assertEquals(genesisBlock.getHash(), block1Prime.getPreviousBlockHash());
//
//            final Block block2Prime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain2.INVALID_BLOCK_2)); // Spends a transaction within block1DoublePrime...
//            Assert.assertEquals(block1Prime.getHash(), block2Prime.getPreviousBlockHash());
//
//            final Block block1DoublePrime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain5.BLOCK_1));
//            Assert.assertEquals(genesisBlock.getHash(), block1DoublePrime.getPreviousBlockHash());
//
//            final TransactionValidatorFactory transactionValidatorFactory = new TransactionValidatorFactory();
//
//            final BlockValidator blockValidator = new BlockValidator(_readUncomittedDatabaseManagerFactory, transactionValidatorFactory, NetworkTime.MAX_VALUE, new FakeMedianBlockTime());
//
//            final BlockchainSegmentId genesisBlockchainSegmentId;
//            {
//                final BlockId genesisBlockId;
//                synchronized (BlockHeaderDatabaseManager.MUTEX) {
//                    genesisBlockId = blockDatabaseManager.insertBlock(genesisBlock);
//                }
//                genesisBlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(genesisBlockId);
//                // Assert.assertTrue(blockValidator.validateBlock(genesisBlockchainSegmentId, genesisBlock)); // NOTE: This assertion is disabled for the genesis block. (The difficulty calculation for this block fails, but it's the genesis block, so it's likely not applicable.)
//            }
//
//            synchronized (BlockHeaderDatabaseManager.MUTEX) {
//                final BlockId block1PrimeId = blockDatabaseManager.insertBlock(block1Prime);
//                Assert.assertTrue(blockValidator.validateBlock(block1PrimeId, block1Prime).isValid);
//            }
//
//            synchronized (BlockHeaderDatabaseManager.MUTEX) {
//                final BlockId block1DoublePrimeId = blockDatabaseManager.insertBlock(block1DoublePrime);
//                Assert.assertTrue(blockValidator.validateBlock(block1DoublePrimeId, block1DoublePrime).isValid);
//            }
//
//            // TransactionInput 0:4A23572C0048299E956AE25262B3C3E75D984A0CCE36B9C60E9A741E14E099F7 should exist within the database, however, it should exist only within a separate chain...
//            final java.util.List<Row> rows = databaseConnection.query(
//                new Query("SELECT transaction_outputs.id FROM transaction_outputs INNER JOIN transactions ON transactions.id = transaction_outputs.transaction_id WHERE transactions.hash = ? AND transaction_outputs.`index` = ?")
//                    .setParameter("4A23572C0048299E956AE25262B3C3E75D984A0CCE36B9C60E9A741E14E099F7")
//                    .setParameter("0")
//            );
//            Assert.assertTrue(rows.size() > 0);
//
//            // Action
//            Boolean block2PrimeIsValid;
//            try {
//                final BlockId block2Id;
//                synchronized (BlockHeaderDatabaseManager.MUTEX) {
//                    block2Id = blockDatabaseManager.insertBlock(block2Prime);
//                }
//                block2PrimeIsValid = blockValidator.validateBlock(block2Id, block2Prime).isValid;
//            }
//            catch (final DatabaseException exception) {
//                block2PrimeIsValid = false;
//            }
//
//            // Assert
//            Assert.assertFalse(block2PrimeIsValid);
//        }
        Assert.fail();
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