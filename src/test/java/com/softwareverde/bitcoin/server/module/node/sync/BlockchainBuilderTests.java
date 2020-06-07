package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.core.BlockProcessorContext;
import com.softwareverde.bitcoin.context.core.BlockchainBuilderContext;
import com.softwareverde.bitcoin.context.core.PendingBlockLoaderContext;
import com.softwareverde.bitcoin.context.core.TransactionValidatorContext;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.server.module.node.BlockProcessor;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode.FullNodePendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.OrphanedTransactionsCache;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.blockloader.PendingBlockLoader;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.FakeBlockStore;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.test.fake.FakeUnspentTransactionOutputContext;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorCore;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorTests;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.security.secp256k1.key.PrivateKey;
import com.softwareverde.util.HexUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BlockchainBuilderTests extends IntegrationTest {

    /**
     * FakeMutableBlock does not require a proper Hash to be valid.
     *  This modification allows the block to be valid without the required amount of Proof of Work.
     */
    public static class FakeMutableBlock extends MutableBlock {
        @Override
        public Boolean isValid() { return true; }
    }

    /**
     * A BlockDownloader StatusMonitor that is always in the `ACTIVE` state.
     */
    public static final BlockDownloader.StatusMonitor FAKE_DOWNLOAD_STATUS_MONITOR = new SleepyService.StatusMonitor() {
        @Override
        public SleepyService.Status getStatus() {
            return SleepyService.Status.ACTIVE;
        }
    };

    /**
     * A BlockInflater/Deflater that prevents the Block's hash from being evaluated.
     */
    public static final BlockInflaters FAKE_BLOCK_INFLATERS = new BlockInflaters() {
        protected final BlockDeflater _blockDeflater = new BlockDeflater();

        @Override
        public BlockInflater getBlockInflater() {
            return new BlockInflater() {
                @Override
                protected MutableBlock _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
                    final Block originalBlock = super._fromByteArrayReader(byteArrayReader);
                    return new MutableBlock(originalBlock) {
                        @Override
                        public Boolean isValid() {
                            return true;
                        }
                    };
                }
            };
        }

        @Override
        public BlockDeflater getBlockDeflater() {
            return _blockDeflater;
        }
    };

    /**
     * A dummy BlockDownloadRequester that does nothing.
     */
    public static final BlockDownloadRequester FAKE_BLOCK_DOWNLOAD_REQUESTER = new BlockDownloadRequester() {
        @Override
        public void requestBlock(final BlockHeader blockHeader) { }

        @Override
        public void requestBlock(final Sha256Hash blockHash, final Sha256Hash previousBlockHash) { }
    };

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
        final FakeBlockStore blockStore = new FakeBlockStore();
        final FakeBitcoinNodeManager bitcoinNodeManager = new FakeBitcoinNodeManager();
        final OrphanedTransactionsCache orphanedTransactionsCache = new OrphanedTransactionsCache();

        final BlockProcessorContext blockProcessorContext = new BlockProcessorContext(_masterInflater, blockStore, _fullNodeDatabaseManagerFactory, new MutableNetworkTime(), _synchronizationStatus);
        final PendingBlockLoaderContext pendingBlockLoaderContext = new PendingBlockLoaderContext(_masterInflater, _fullNodeDatabaseManagerFactory, _threadPool);
        final BlockchainBuilderContext blockchainBuilderContext = new BlockchainBuilderContext(_masterInflater, _fullNodeDatabaseManagerFactory, bitcoinNodeManager, _threadPool);

        final BlockProcessor blockProcessor = new BlockProcessor(blockProcessorContext, orphanedTransactionsCache);
        final PendingBlockLoader pendingBlockLoader = new PendingBlockLoader(pendingBlockLoaderContext, 1);

        final BlockchainBuilder blockchainBuilder = new BlockchainBuilder(blockchainBuilderContext, blockProcessor, pendingBlockLoader, BlockchainBuilderTests.FAKE_DOWNLOAD_STATUS_MONITOR, null);

        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockInflater blockInflater = _masterInflater.getBlockInflater();
            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();
            for (final String blockData : new String[]{ BlockData.MainChain.GENESIS_BLOCK, BlockData.MainChain.BLOCK_1, BlockData.MainChain.BLOCK_2, BlockData.MainChain.BLOCK_3, BlockData.MainChain.BLOCK_4, BlockData.MainChain.BLOCK_5 }) {
                final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
                pendingBlockDatabaseManager.storeBlock(block);
            }
        }

        // Action
        blockchainBuilder.start();
        Thread.sleep(1000L);
        blockchainBuilder.stop();

        // Assert
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            final BlockchainSegmentId blockchainSegmentId = BlockchainSegmentId.wrap(1L);
            Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, 0L));
            Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, 1L));
            Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, 2L));
            Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, 3L));
            Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, 4L));
            Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, 5L));
        }
    }

    @Test
    public void block_should_be_valid_if_output_is_spent_only_on_a_different_chain() throws Exception {
        // This test creates a (fake) Block03 with a spendable coinbase, then creates two contentious (fake) Block04s.
        //  Both versions of Block04 spend the coinbase of Block03, and both chains should be valid. (Coinbase maturity must be disabled.)

        final FakeBlockStore blockStore = new FakeBlockStore();
        final FakeBitcoinNodeManager bitcoinNodeManager = new FakeBitcoinNodeManager();
        final OrphanedTransactionsCache orphanedTransactionsCache = new OrphanedTransactionsCache();
        final BlockInflaters blockInflaters = BlockchainBuilderTests.FAKE_BLOCK_INFLATERS;

        final BlockProcessorContext blockProcessorContext = new BlockProcessorContext(blockInflaters, blockStore, _fullNodeDatabaseManagerFactory, new MutableNetworkTime(), _synchronizationStatus);
        final PendingBlockLoaderContext pendingBlockLoaderContext = new PendingBlockLoaderContext(blockInflaters, _fullNodeDatabaseManagerFactory, _threadPool);
        final BlockchainBuilderContext blockchainBuilderContext = new BlockchainBuilderContext(blockInflaters, _fullNodeDatabaseManagerFactory, bitcoinNodeManager, _threadPool);

        final BlockProcessor blockProcessor = new BlockProcessor(blockProcessorContext, orphanedTransactionsCache);
        final PendingBlockLoader pendingBlockLoader = new PendingBlockLoader(pendingBlockLoaderContext, 1);

        final Sha256Hash block02Hash;
        {
            final BlockInflater blockInflater = _masterInflater.getBlockInflater();
            final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_2));
            block02Hash = block.getHash();
        }

        final PrivateKey privateKey = PrivateKey.createNewKey();

        final Block fakeBlock03;
        {
            final MutableBlock mutableBlock = new FakeMutableBlock();

            mutableBlock.setDifficulty(Difficulty.BASE_DIFFICULTY);
            mutableBlock.setNonce(0L);
            mutableBlock.setTimestamp(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP);
            mutableBlock.setVersion(Block.VERSION);

            // Create a transaction that will be spent in our signed transaction.
            //  This transaction will create an output that can be spent by our private key.
            final Transaction transactionToSpend = TransactionValidatorTests.createTransactionSpendableByPrivateKey(privateKey);
            mutableBlock.addTransaction(transactionToSpend);
            System.out.println("transactionToSpend=" + transactionToSpend.getHash());

            mutableBlock.setPreviousBlockHash(block02Hash);
            fakeBlock03 = mutableBlock;
        }

        final Transaction signedTransactionSpendingCoinbase;
        {
            final AddressInflater addressInflater = new AddressInflater();

            final Transaction transactionToSpend = fakeBlock03.getCoinbaseTransaction();

            // Create an unsigned transaction that spends our previous transaction, and send our payment to an irrelevant address.
            final Transaction unsignedTransaction;
            {
                final MutableTransaction mutableTransaction = new MutableTransaction();
                mutableTransaction.setVersion(Transaction.VERSION);
                mutableTransaction.setLockTime(LockTime.MAX_TIMESTAMP);

                final TransactionInput transactionInput;
                {
                    final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
                    mutableTransactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
                    mutableTransactionInput.setPreviousOutputTransactionHash(transactionToSpend.getHash());
                    mutableTransactionInput.setPreviousOutputIndex(0);
                    mutableTransactionInput.setUnlockingScript(UnlockingScript.EMPTY_SCRIPT);
                    transactionInput = mutableTransactionInput;
                }
                mutableTransaction.addTransactionInput(transactionInput);

                final TransactionOutput transactionOutput;
                {
                    final MutableTransactionOutput mutableTransactionOutput = new MutableTransactionOutput();
                    mutableTransactionOutput.setIndex(0);
                    mutableTransactionOutput.setAmount(50L * Transaction.SATOSHIS_PER_BITCOIN);

                    final LockingScript lockingScript = ScriptBuilder.payToAddress(addressInflater.uncompressedFromBase58Check("1HrXm9WZF7LBm3HCwCBgVS3siDbk5DYCuW"));
                    mutableTransactionOutput.setLockingScript(lockingScript);
                    transactionOutput = mutableTransactionOutput;
                }
                mutableTransaction.addTransactionOutput(transactionOutput);

                unsignedTransaction = mutableTransaction;
            }

            signedTransactionSpendingCoinbase = TransactionValidatorTests.signTransaction(transactionToSpend, unsignedTransaction, privateKey);

            { // Ensure the transaction would normally be valid on its own...
                final FakeUnspentTransactionOutputContext unspentTransactionOutputContext = new FakeUnspentTransactionOutputContext();
                unspentTransactionOutputContext.addTransaction(transactionToSpend, null, 2L, false);

                final TransactionValidatorContext transactionValidatorContext = new TransactionValidatorContext(new MutableNetworkTime(), MedianBlockTime.MAX_VALUE, unspentTransactionOutputContext);
                final TransactionValidator transactionValidator = new TransactionValidatorCore(transactionValidatorContext);

                final Boolean isValid = transactionValidator.validateTransaction(3L, signedTransactionSpendingCoinbase);
                Assert.assertTrue(isValid);
            }
        }

        final Block fakeBlock04aSpendingBlock03Coinbase;
        { // Spend the coinbase in a block...
            final MutableBlock mutableBlock = new FakeMutableBlock();

            mutableBlock.setDifficulty(Difficulty.BASE_DIFFICULTY);
            mutableBlock.setNonce(0L);
            mutableBlock.setTimestamp(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP);
            mutableBlock.setVersion(Block.VERSION);
            mutableBlock.setPreviousBlockHash(fakeBlock03.getHash());

            final Transaction regularCoinbaseTransaction = TransactionValidatorTests.createTransactionSpendableByPrivateKey(PrivateKey.createNewKey());

            mutableBlock.addTransaction(regularCoinbaseTransaction);
            mutableBlock.addTransaction(signedTransactionSpendingCoinbase);

            fakeBlock04aSpendingBlock03Coinbase = mutableBlock;
        }

        final Block fakeBlock04bSpendingBlock03Coinbase;
        { // Spend the coinbase on a separate chain by creating another modified block #3 ...
            final MutableBlock mutableBlock = new FakeMutableBlock();

            mutableBlock.setDifficulty(Difficulty.BASE_DIFFICULTY);
            mutableBlock.setNonce(0L);
            mutableBlock.setTimestamp(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP);
            mutableBlock.setVersion(Block.VERSION);
            mutableBlock.setPreviousBlockHash(fakeBlock03.getHash());

            final Transaction regularCoinbaseTransaction = TransactionValidatorTests.createTransactionSpendableByPrivateKey(PrivateKey.createNewKey());

            mutableBlock.addTransaction(regularCoinbaseTransaction);
            mutableBlock.addTransaction(signedTransactionSpendingCoinbase);

            fakeBlock04bSpendingBlock03Coinbase = mutableBlock;
        }

        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockInflater blockInflater = _masterInflater.getBlockInflater();
            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();
            for (final String blockData : new String[]{ BlockData.MainChain.GENESIS_BLOCK, BlockData.MainChain.BLOCK_1, BlockData.MainChain.BLOCK_2 }) {
                final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
                pendingBlockDatabaseManager.storeBlock(block);
            }

            for (final Block block : new Block[] { fakeBlock03 }) {
                pendingBlockDatabaseManager.storeBlock(block);
            }

            for (final Block block : new Block[] { fakeBlock04aSpendingBlock03Coinbase, fakeBlock04bSpendingBlock03Coinbase }) {
                pendingBlockDatabaseManager.storeBlock(block);
            }
        }

        // NOTE: The blockchainBuilder checks for the Genesis block upon instantiation.
        final BlockchainBuilder blockchainBuilder = new BlockchainBuilder(blockchainBuilderContext, blockProcessor, pendingBlockLoader, BlockchainBuilderTests.FAKE_DOWNLOAD_STATUS_MONITOR, BlockchainBuilderTests.FAKE_BLOCK_DOWNLOAD_REQUESTER);

        // Action
        blockchainBuilder.start();
        Thread.sleep(1000L);
        blockchainBuilder.stop();

        // Assert
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();

            final BlockchainSegmentId mainBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            {
                Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(mainBlockchainSegmentId, 0L));
                Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(mainBlockchainSegmentId, 1L));
                Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(mainBlockchainSegmentId, 2L));
                Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(mainBlockchainSegmentId, 3L));

                final BlockId block4aId = blockHeaderDatabaseManager.getBlockIdAtHeight(mainBlockchainSegmentId, 4L);
                Assert.assertNotNull(block4aId);
                final Sha256Hash block4aHash = blockHeaderDatabaseManager.getBlockHash(block4aId);
                Assert.assertEquals(fakeBlock04aSpendingBlock03Coinbase.getHash(), block4aHash);
            }

            {
                final BlockId block4bId = blockHeaderDatabaseManager.getBlockHeaderId(fakeBlock04bSpendingBlock03Coinbase.getHash());
                Assert.assertNotNull(block4bId);

                final BlockchainSegmentId altBlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(block4bId);
                Assert.assertNotEquals(mainBlockchainSegmentId, altBlockchainSegmentId); // The original blockHeader should not have been usurped by its contentious brother...

                Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(altBlockchainSegmentId, 0L));
                Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(altBlockchainSegmentId, 1L));
                Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(altBlockchainSegmentId, 2L));
                Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(altBlockchainSegmentId, 3L));
                Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(altBlockchainSegmentId, 4L));
            }
        }
    }

    @Test
    public void should_not_validate_transaction_when_transaction_output_is_only_found_on_separate_fork() throws Exception {
        // This test creates two (fake) Block03s, each with a spendable coinbase.
        //  The second Block03 (`Block03b`) spends the first Block03 (`Block03a`)'s coinbase, which is invalid.
        //  The insertion order is Genesis -> Block01 -> Block02 -> Block03a -> Block03b

        final FakeBlockStore blockStore = new FakeBlockStore();
        final FakeBitcoinNodeManager bitcoinNodeManager = new FakeBitcoinNodeManager();
        final OrphanedTransactionsCache orphanedTransactionsCache = new OrphanedTransactionsCache();
        final BlockInflaters blockInflaters = BlockchainBuilderTests.FAKE_BLOCK_INFLATERS;

        final BlockProcessorContext blockProcessorContext = new BlockProcessorContext(blockInflaters, blockStore, _fullNodeDatabaseManagerFactory, new MutableNetworkTime(), _synchronizationStatus);
        final PendingBlockLoaderContext pendingBlockLoaderContext = new PendingBlockLoaderContext(blockInflaters, _fullNodeDatabaseManagerFactory, _threadPool);
        final BlockchainBuilderContext blockchainBuilderContext = new BlockchainBuilderContext(blockInflaters, _fullNodeDatabaseManagerFactory, bitcoinNodeManager, _threadPool);

        final BlockProcessor blockProcessor = new BlockProcessor(blockProcessorContext, orphanedTransactionsCache);
        final PendingBlockLoader pendingBlockLoader = new PendingBlockLoader(pendingBlockLoaderContext, 1);

        final Sha256Hash block02Hash;
        {
            final BlockInflater blockInflater = _masterInflater.getBlockInflater();
            final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_2));
            block02Hash = block.getHash();
        }

        final PrivateKey privateKey = PrivateKey.createNewKey();

        final Block fakeBlock03a;
        {
            final MutableBlock mutableBlock = new FakeMutableBlock();

            mutableBlock.setDifficulty(Difficulty.BASE_DIFFICULTY);
            mutableBlock.setNonce(0L);
            mutableBlock.setTimestamp(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP);
            mutableBlock.setVersion(Block.VERSION);

            // Create a transaction that will be spent in our signed transaction.
            //  This transaction will create an output that can be spent by our private key.
            final Transaction transactionToSpend = TransactionValidatorTests.createTransactionSpendableByPrivateKey(privateKey);
            mutableBlock.addTransaction(transactionToSpend);
            System.out.println("transactionToSpend=" + transactionToSpend.getHash());

            mutableBlock.setPreviousBlockHash(block02Hash);
            fakeBlock03a = mutableBlock;
        }

        final Transaction signedTransactionSpendingFakeBlock3aCoinbase;
        {
            final AddressInflater addressInflater = new AddressInflater();

            final Transaction transactionToSpend = fakeBlock03a.getCoinbaseTransaction();

            // Create an unsigned transaction that spends our previous transaction, and send our payment to an irrelevant address.
            final Transaction unsignedTransaction;
            {
                final MutableTransaction mutableTransaction = new MutableTransaction();
                mutableTransaction.setVersion(Transaction.VERSION);
                mutableTransaction.setLockTime(LockTime.MAX_TIMESTAMP);

                final TransactionInput transactionInput;
                {
                    final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
                    mutableTransactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
                    mutableTransactionInput.setPreviousOutputTransactionHash(transactionToSpend.getHash());
                    mutableTransactionInput.setPreviousOutputIndex(0);
                    mutableTransactionInput.setUnlockingScript(UnlockingScript.EMPTY_SCRIPT);
                    transactionInput = mutableTransactionInput;
                }
                mutableTransaction.addTransactionInput(transactionInput);

                final TransactionOutput transactionOutput;
                {
                    final MutableTransactionOutput mutableTransactionOutput = new MutableTransactionOutput();
                    mutableTransactionOutput.setIndex(0);
                    mutableTransactionOutput.setAmount(50L * Transaction.SATOSHIS_PER_BITCOIN);

                    final LockingScript lockingScript = ScriptBuilder.payToAddress(addressInflater.uncompressedFromBase58Check("1HrXm9WZF7LBm3HCwCBgVS3siDbk5DYCuW"));
                    mutableTransactionOutput.setLockingScript(lockingScript);
                    transactionOutput = mutableTransactionOutput;
                }
                mutableTransaction.addTransactionOutput(transactionOutput);

                unsignedTransaction = mutableTransaction;
            }

            signedTransactionSpendingFakeBlock3aCoinbase = TransactionValidatorTests.signTransaction(transactionToSpend, unsignedTransaction, privateKey);

            { // Ensure the transaction would normally be valid on its own...
                final FakeUnspentTransactionOutputContext unspentTransactionOutputContext = new FakeUnspentTransactionOutputContext();
                unspentTransactionOutputContext.addTransaction(transactionToSpend, null, 2L, false);

                final TransactionValidatorContext transactionValidatorContext = new TransactionValidatorContext(new MutableNetworkTime(), MedianBlockTime.MAX_VALUE, unspentTransactionOutputContext);
                final TransactionValidator transactionValidator = new TransactionValidatorCore(transactionValidatorContext);

                final Boolean isValid = transactionValidator.validateTransaction(3L, signedTransactionSpendingFakeBlock3aCoinbase);
                Assert.assertTrue(isValid);
            }
        }

        final Block fakeBlock03bSpendingBlock03aCoinbase;
        { // Spend the coinbase in a block...
            final MutableBlock mutableBlock = new FakeMutableBlock();

            mutableBlock.setDifficulty(Difficulty.BASE_DIFFICULTY);
            mutableBlock.setNonce(0L);
            mutableBlock.setTimestamp(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP);
            mutableBlock.setVersion(Block.VERSION);
            mutableBlock.setPreviousBlockHash(block02Hash);

            final Transaction regularCoinbaseTransaction = TransactionValidatorTests.createTransactionSpendableByPrivateKey(PrivateKey.createNewKey());

            mutableBlock.addTransaction(regularCoinbaseTransaction);
            mutableBlock.addTransaction(signedTransactionSpendingFakeBlock3aCoinbase);

            fakeBlock03bSpendingBlock03aCoinbase = mutableBlock;
        }

        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockInflater blockInflater = _masterInflater.getBlockInflater();
            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();
            for (final String blockData : new String[]{ BlockData.MainChain.GENESIS_BLOCK, BlockData.MainChain.BLOCK_1, BlockData.MainChain.BLOCK_2 }) {
                final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
                pendingBlockDatabaseManager.storeBlock(block);
            }

            for (final Block block : new Block[] { fakeBlock03a, fakeBlock03bSpendingBlock03aCoinbase }) {
                pendingBlockDatabaseManager.storeBlock(block);
            }
        }

        // NOTE: The blockchainBuilder checks for the Genesis block upon instantiation.
        final BlockchainBuilder blockchainBuilder = new BlockchainBuilder(blockchainBuilderContext, blockProcessor, pendingBlockLoader, BlockchainBuilderTests.FAKE_DOWNLOAD_STATUS_MONITOR, BlockchainBuilderTests.FAKE_BLOCK_DOWNLOAD_REQUESTER);

        // Action
        blockchainBuilder.start();
        Thread.sleep(1000L);
        blockchainBuilder.stop();

        // Assert
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();

            final BlockchainSegmentId mainBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(mainBlockchainSegmentId, 0L));
            Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(mainBlockchainSegmentId, 1L));
            Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(mainBlockchainSegmentId, 2L));

            final BlockId block3aId = blockHeaderDatabaseManager.getBlockIdAtHeight(mainBlockchainSegmentId, 3L);
            Assert.assertNotNull(block3aId);
            final Sha256Hash block4aHash = blockHeaderDatabaseManager.getBlockHash(block3aId);
            Assert.assertEquals(fakeBlock03a.getHash(), block4aHash);

            final BlockId block3bId = blockHeaderDatabaseManager.getBlockHeaderId(fakeBlock03bSpendingBlock03aCoinbase.getHash());
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final Boolean block3bWasValid = blockDatabaseManager.hasTransactions(block3bId);
            Assert.assertFalse(block3bWasValid);
        }
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