package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.bip.CoreUpgradeSchedule;
import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.context.core.BlockProcessorContext;
import com.softwareverde.bitcoin.context.core.BlockchainBuilderContext;
import com.softwareverde.bitcoin.context.core.PendingBlockLoaderContext;
import com.softwareverde.bitcoin.context.core.TransactionValidatorContext;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.inflater.TransactionInflaters;
import com.softwareverde.bitcoin.server.module.node.BlockProcessor;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode.FullNodePendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.blockloader.PendingBlockLoader;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.FakeBlockStore;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.test.fake.FakeStaticMedianBlockTimeContext;
import com.softwareverde.bitcoin.test.fake.FakeUnspentTransactionOutputContext;
import com.softwareverde.bitcoin.test.util.BlockTestUtil;
import com.softwareverde.bitcoin.test.util.TransactionTestUtil;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.signer.TransactionOutputRepository;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidationResult;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorCore;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BlockchainBuilderTests extends IntegrationTest {

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

        @Override
        public void requestBlocks(final List<BlockHeader> blockHeaders) { }
    };

    /**
     * FakeBitcoinNodeManager is a BitcoinNodeManager that prevents all network traffic.
     */
    public static class FakeBitcoinNodeManager extends BitcoinNodeManager {
        protected static Context _createFakeContext() {
            final Context context = new Context();
            context.maxNodeCount = 0;
            return context;
        }

        public FakeBitcoinNodeManager() {
            super(_createFakeContext());
        }

        @Override
        public List<BitcoinNode> getNodes() {
            return new MutableList<BitcoinNode>(0);
        }
    }

    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @After @Override
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_synchronize_pending_blocks() throws Exception {
        final FakeBlockStore blockStore = new FakeBlockStore();
        final FakeBitcoinNodeManager bitcoinNodeManager = new FakeBitcoinNodeManager();

        final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();
        final BlockProcessorContext blockProcessorContext = new BlockProcessorContext(_masterInflater, _masterInflater, blockStore, _fullNodeDatabaseManagerFactory, new MutableNetworkTime(), _synchronizationStatus, _difficultyCalculatorFactory, _transactionValidatorFactory, upgradeSchedule);
        final PendingBlockLoaderContext pendingBlockLoaderContext = new PendingBlockLoaderContext(_masterInflater, _fullNodeDatabaseManagerFactory, _threadPool);
        final BlockchainBuilderContext blockchainBuilderContext = new BlockchainBuilderContext(_masterInflater, _fullNodeDatabaseManagerFactory, bitcoinNodeManager, _threadPool);

        final BlockProcessor blockProcessor = new BlockProcessor(blockProcessorContext);
        final PendingBlockLoader pendingBlockLoader = new PendingBlockLoader(pendingBlockLoaderContext, 1);

        final BlockchainBuilder blockchainBuilder = new BlockchainBuilder(blockchainBuilderContext, blockProcessor, pendingBlockLoader, BlockchainBuilderTests.FAKE_DOWNLOAD_STATUS_MONITOR, null);

        final Block[] blocks;
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockInflater blockInflater = _masterInflater.getBlockInflater();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();

            blocks = new Block[6];
            int blockHeight = 0;
            for (final String blockData : new String[]{ BlockData.MainChain.GENESIS_BLOCK, BlockData.MainChain.BLOCK_1, BlockData.MainChain.BLOCK_2, BlockData.MainChain.BLOCK_3, BlockData.MainChain.BLOCK_4, BlockData.MainChain.BLOCK_5 }) {
                final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));

                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockHeaderDatabaseManager.storeBlockHeader(block);
                }

                pendingBlockDatabaseManager.storeBlock(block);
                blocks[blockHeight] = block;
                blockHeight += 1;
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

            // Ensure the coinbase UTXOs were added to the UTXO set.
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
            for (int blockHeight = 1; blockHeight < 6L; ++blockHeight) {
                final Block block = blocks[blockHeight];
                final Transaction transaction = block.getCoinbaseTransaction();
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transaction.getHash(), 0);
                final TransactionOutput unspentTransactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(transactionOutputIdentifier);
                Assert.assertNotNull(unspentTransactionOutput);
            }
        }
    }

    @Test
    public void block_should_be_valid_if_output_is_spent_only_on_a_different_chain() throws Exception {
        // This test creates a (fake) Block03 with a spendable coinbase, then creates two contentious (fake) Block04s.
        //  Both versions of Block04 spend the coinbase of Block03, and both chains should be valid. (Coinbase maturity must be disabled.)

        final TransactionInflaters transactionInflaters = _masterInflater;
        final AddressInflater addressInflater = new AddressInflater();
        final FakeBlockStore blockStore = new FakeBlockStore();
        final FakeBitcoinNodeManager bitcoinNodeManager = new FakeBitcoinNodeManager();
        final BlockInflaters blockInflaters = BlockchainBuilderTests.FAKE_BLOCK_INFLATERS;
        final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();

        final BlockProcessorContext blockProcessorContext = new BlockProcessorContext(blockInflaters, transactionInflaters, blockStore, _fullNodeDatabaseManagerFactory, new MutableNetworkTime(), _synchronizationStatus, _difficultyCalculatorFactory, _transactionValidatorFactory, upgradeSchedule);
        final PendingBlockLoaderContext pendingBlockLoaderContext = new PendingBlockLoaderContext(blockInflaters, _fullNodeDatabaseManagerFactory, _threadPool);
        final BlockchainBuilderContext blockchainBuilderContext = new BlockchainBuilderContext(blockInflaters, _fullNodeDatabaseManagerFactory, bitcoinNodeManager, _threadPool);

        final BlockProcessor blockProcessor = new BlockProcessor(blockProcessorContext);
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
            final MutableBlock mutableBlock = BlockTestUtil.createBlock();
            mutableBlock.setPreviousBlockHash(block02Hash);

            // Create a transaction that will be spent in the test's signed transaction.
            //  This transaction will create an output that can be spent by the test's private key.
            final Transaction transactionToSpend = TransactionTestUtil.createCoinbaseTransactionSpendableByPrivateKey(privateKey);
            mutableBlock.addTransaction(transactionToSpend);

            fakeBlock03 = mutableBlock;
        }

        final TransactionOutputIdentifier spentTransactionOutputIdentifier;
        final Transaction signedTransactionSpendingCoinbase;
        {
            final Transaction transactionToSpend = fakeBlock03.getCoinbaseTransaction();

            // Create an unsigned transaction that spends the test's previous transaction, and send the test's payment to an irrelevant address.
            final Transaction unsignedTransaction;
            {
                spentTransactionOutputIdentifier = new TransactionOutputIdentifier(transactionToSpend.getHash(), 0);

                final TransactionInput transactionInput = TransactionTestUtil.createTransactionInput(spentTransactionOutputIdentifier);
                final TransactionOutput transactionOutput = TransactionTestUtil.createTransactionOutput(
                    (50L * Transaction.SATOSHIS_PER_BITCOIN),
                    addressInflater.fromPrivateKey(privateKey, true)
                );

                final MutableTransaction mutableTransaction = TransactionTestUtil.createTransaction();
                mutableTransaction.addTransactionInput(transactionInput);
                mutableTransaction.addTransactionOutput(transactionOutput);
                unsignedTransaction = mutableTransaction;
            }

            final TransactionOutputRepository transactionOutputRepository = TransactionTestUtil.createTransactionOutputRepository(transactionToSpend);
            signedTransactionSpendingCoinbase = TransactionTestUtil.signTransaction(transactionOutputRepository, unsignedTransaction, privateKey);

            { // Ensure the transaction would normally be valid on its own...
                final FakeUnspentTransactionOutputContext unspentTransactionOutputContext = new FakeUnspentTransactionOutputContext();
                unspentTransactionOutputContext.addTransaction(transactionToSpend, null, 2L, false);

                final TransactionValidatorContext transactionValidatorContext = new TransactionValidatorContext(transactionInflaters, new MutableNetworkTime(), FakeStaticMedianBlockTimeContext.MAX_MEDIAN_BLOCK_TIME, unspentTransactionOutputContext, upgradeSchedule);
                final TransactionValidator transactionValidator = new TransactionValidatorCore(transactionValidatorContext);

                final TransactionValidationResult transactionValidationResult = transactionValidator.validateTransaction(3L, signedTransactionSpendingCoinbase);
                Assert.assertTrue(transactionValidationResult.isValid);
            }
        }

        final Transaction chainedTransactionForBlock4b; // Since the signedTransactionSpendingCoinbase is duplicated between Block4a and Block4b, this transaction is unique to Block4b to test that its outputs are not added to the UTXO set.
        {
            final TransactionOutputIdentifier transactionOutputIdentifierToSpend = new TransactionOutputIdentifier(signedTransactionSpendingCoinbase.getHash(), 0);

            final TransactionInput transactionInput = TransactionTestUtil.createTransactionInput(transactionOutputIdentifierToSpend);
            final TransactionOutput transactionOutput = TransactionTestUtil.createTransactionOutput(addressInflater.fromPrivateKey(privateKey, true));

            final MutableTransaction mutableTransaction = TransactionTestUtil.createTransaction();
            mutableTransaction.addTransactionInput(transactionInput);
            mutableTransaction.addTransactionOutput(transactionOutput);

            final TransactionOutputRepository transactionOutputRepository = TransactionTestUtil.createTransactionOutputRepository(signedTransactionSpendingCoinbase);
            chainedTransactionForBlock4b = TransactionTestUtil.signTransaction(transactionOutputRepository, mutableTransaction, privateKey);
        }

        final Block fakeBlock04aSpendingBlock03Coinbase;
        { // Spend the coinbase in a block...
            final MutableBlock mutableBlock = BlockTestUtil.createBlock();
            mutableBlock.setPreviousBlockHash(fakeBlock03.getHash());

            final Transaction regularCoinbaseTransaction = TransactionTestUtil.createCoinbaseTransactionSpendableByPrivateKey(PrivateKey.createNewKey());
            mutableBlock.addTransaction(regularCoinbaseTransaction);

            mutableBlock.addTransaction(signedTransactionSpendingCoinbase);

            fakeBlock04aSpendingBlock03Coinbase = mutableBlock;
        }

        final Block fakeBlock04bSpendingBlock03Coinbase;
        { // Spend the coinbase on a separate chain by creating another modified block #3 ...
            final MutableBlock mutableBlock = BlockTestUtil.createBlock();
            mutableBlock.setPreviousBlockHash(fakeBlock03.getHash());

            final Transaction regularCoinbaseTransaction = TransactionTestUtil.createCoinbaseTransactionSpendableByPrivateKey(PrivateKey.createNewKey());
            mutableBlock.addTransaction(regularCoinbaseTransaction);

            mutableBlock.addTransaction(signedTransactionSpendingCoinbase);
            mutableBlock.addTransaction(chainedTransactionForBlock4b);

            fakeBlock04bSpendingBlock03Coinbase = mutableBlock;
        }

        final Block[] mainChainBlocks;
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockInflater blockInflater = _masterInflater.getBlockInflater();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();;
            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();

            int blockHeight = 0;
            mainChainBlocks = new Block[5];

            for (final String blockData : new String[]{ BlockData.MainChain.GENESIS_BLOCK, BlockData.MainChain.BLOCK_1, BlockData.MainChain.BLOCK_2 }) {
                final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockHeaderDatabaseManager.storeBlockHeader(block);
                }

                pendingBlockDatabaseManager.storeBlock(block);

                mainChainBlocks[blockHeight] = block;
                blockHeight += 1;
            }

            for (final Block block : new Block[] { fakeBlock03 }) {
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockHeaderDatabaseManager.storeBlockHeader(block);
                }

                pendingBlockDatabaseManager.storeBlock(block);

                mainChainBlocks[blockHeight] = block;
                blockHeight += 1;
            }

            for (final Block block : new Block[] { fakeBlock04aSpendingBlock03Coinbase, fakeBlock04bSpendingBlock03Coinbase }) {
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockHeaderDatabaseManager.storeBlockHeader(block);
                }

                pendingBlockDatabaseManager.storeBlock(block);
            }

            mainChainBlocks[blockHeight] = fakeBlock04aSpendingBlock03Coinbase;
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

            { // Ensure the UTXO set was maintained correctly...
                final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();

                { // Should exclude genesis UTXO...
                    final Block block = mainChainBlocks[0];
                    final Transaction transaction = block.getCoinbaseTransaction();
                    final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transaction.getHash(), 0);
                    final TransactionOutput unspentTransactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(transactionOutputIdentifier);
                    Assert.assertNull(unspentTransactionOutput);
                }

                for (int i = 1; i < mainChainBlocks.length; ++i) {
                    final Block block = mainChainBlocks[i];

                    for (final Transaction transaction : block.getTransactions()) {
                        final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transaction.getHash(), 0);
                        final TransactionOutput unspentTransactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(transactionOutputIdentifier);

                        if (Util.areEqual(spentTransactionOutputIdentifier, transactionOutputIdentifier)) {
                            Assert.assertNull(unspentTransactionOutput);
                        }
                        else {
                            Assert.assertNotNull(unspentTransactionOutput);
                        }
                    }
                }

                { // Ensure the transactionOutput unique to Block4b is not included in the UTXO set.
                    // NOTE: `signedTransactionSpendingCoinbase` is shared between Block4a and Block4b, so therefore its UTXO should be included within the set.
                    final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(chainedTransactionForBlock4b.getHash(), 0);
                    final TransactionOutput unspentTransactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(transactionOutputIdentifier);
                    Assert.assertNull(unspentTransactionOutput);
                }
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
        final BlockInflaters blockInflaters = BlockchainBuilderTests.FAKE_BLOCK_INFLATERS;
        final TransactionInflaters transactionInflaters = _masterInflater;
        final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();

        final BlockProcessorContext blockProcessorContext = new BlockProcessorContext(blockInflaters, transactionInflaters, blockStore, _fullNodeDatabaseManagerFactory, new MutableNetworkTime(), _synchronizationStatus, _difficultyCalculatorFactory, _transactionValidatorFactory, upgradeSchedule);
        final PendingBlockLoaderContext pendingBlockLoaderContext = new PendingBlockLoaderContext(blockInflaters, _fullNodeDatabaseManagerFactory, _threadPool);
        final BlockchainBuilderContext blockchainBuilderContext = new BlockchainBuilderContext(blockInflaters, _fullNodeDatabaseManagerFactory, bitcoinNodeManager, _threadPool);

        final BlockProcessor blockProcessor = new BlockProcessor(blockProcessorContext);
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
            final MutableBlock mutableBlock = BlockTestUtil.createBlock();
            mutableBlock.setPreviousBlockHash(block02Hash);

            // Create a transaction that will be spent in the test's signed transaction.
            //  This transaction will create an output that can be spent by the test's private key.
            final Transaction transactionToSpend = TransactionTestUtil.createCoinbaseTransactionSpendableByPrivateKey(privateKey);
            mutableBlock.addTransaction(transactionToSpend);

            fakeBlock03a = mutableBlock;
        }

        final Transaction signedTransactionSpendingFakeBlock3aCoinbase;
        {
            final AddressInflater addressInflater = new AddressInflater();

            final Transaction transactionToSpend = fakeBlock03a.getCoinbaseTransaction();

            // Create an unsigned transaction that spends the test's previous transaction, and send the test's payment to an irrelevant address.
            final Transaction unsignedTransaction;
            {
                final MutableTransaction mutableTransaction = TransactionTestUtil.createTransaction();

                final TransactionOutputIdentifier transactionOutputIdentifierToSpend = new TransactionOutputIdentifier(transactionToSpend.getHash(), 0);
                final TransactionInput transactionInput = TransactionTestUtil.createTransactionInput(transactionOutputIdentifierToSpend);
                mutableTransaction.addTransactionInput(transactionInput);

                final TransactionOutput transactionOutput = TransactionTestUtil.createTransactionOutput(
                    addressInflater.fromBase58Check("1HrXm9WZF7LBm3HCwCBgVS3siDbk5DYCuW")
                );
                mutableTransaction.addTransactionOutput(transactionOutput);

                unsignedTransaction = mutableTransaction;
            }

            final TransactionOutputRepository transactionOutputRepository = TransactionTestUtil.createTransactionOutputRepository(transactionToSpend);
            signedTransactionSpendingFakeBlock3aCoinbase = TransactionTestUtil.signTransaction(transactionOutputRepository, unsignedTransaction, privateKey);

            { // Ensure the transaction would normally be valid on its own...
                final FakeUnspentTransactionOutputContext unspentTransactionOutputContext = new FakeUnspentTransactionOutputContext();
                unspentTransactionOutputContext.addTransaction(transactionToSpend, null, 2L, false);

                final TransactionValidatorContext transactionValidatorContext = new TransactionValidatorContext(transactionInflaters, new MutableNetworkTime(), FakeStaticMedianBlockTimeContext.MAX_MEDIAN_BLOCK_TIME, unspentTransactionOutputContext, upgradeSchedule);
                final TransactionValidator transactionValidator = new TransactionValidatorCore(transactionValidatorContext);

                final TransactionValidationResult transactionValidationResult = transactionValidator.validateTransaction(3L, signedTransactionSpendingFakeBlock3aCoinbase);
                Assert.assertTrue(transactionValidationResult.isValid);
            }
        }

        final Block fakeBlock03bSpendingBlock03aCoinbase;
        { // Spend the coinbase in a block...
            final MutableBlock mutableBlock = BlockTestUtil.createBlock();
            mutableBlock.setPreviousBlockHash(block02Hash);

            final Transaction regularCoinbaseTransaction = TransactionTestUtil.createCoinbaseTransactionSpendableByPrivateKey(PrivateKey.createNewKey());

            mutableBlock.addTransaction(regularCoinbaseTransaction);
            mutableBlock.addTransaction(signedTransactionSpendingFakeBlock3aCoinbase);

            fakeBlock03bSpendingBlock03aCoinbase = mutableBlock;
        }

        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockInflater blockInflater = _masterInflater.getBlockInflater();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();
            for (final String blockData : new String[]{ BlockData.MainChain.GENESIS_BLOCK, BlockData.MainChain.BLOCK_1, BlockData.MainChain.BLOCK_2 }) {
                final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockHeaderDatabaseManager.storeBlockHeader(block);
                }

                pendingBlockDatabaseManager.storeBlock(block);
            }

            for (final Block block : new Block[] { fakeBlock03a, fakeBlock03bSpendingBlock03aCoinbase }) {
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockHeaderDatabaseManager.storeBlockHeader(block);
                }

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
    public void should_not_validate_block_that_has_two_transactions_spending_the_same_output() throws Exception {
        // This test creates a (fake) Block03, with a spendable coinbase, then creates a (fake) Block04 that spends the Block03's coinbase twice, which is invalid.

        final FakeBlockStore blockStore = new FakeBlockStore();
        final FakeBitcoinNodeManager bitcoinNodeManager = new FakeBitcoinNodeManager();
        final BlockInflaters blockInflaters = BlockchainBuilderTests.FAKE_BLOCK_INFLATERS;
        final TransactionInflaters transactionInflaters = _masterInflater;
        final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();

        final BlockProcessorContext blockProcessorContext = new BlockProcessorContext(blockInflaters, transactionInflaters, blockStore, _fullNodeDatabaseManagerFactory, new MutableNetworkTime(), _synchronizationStatus, _difficultyCalculatorFactory, _transactionValidatorFactory, upgradeSchedule);
        final PendingBlockLoaderContext pendingBlockLoaderContext = new PendingBlockLoaderContext(blockInflaters, _fullNodeDatabaseManagerFactory, _threadPool);
        final BlockchainBuilderContext blockchainBuilderContext = new BlockchainBuilderContext(blockInflaters, _fullNodeDatabaseManagerFactory, bitcoinNodeManager, _threadPool);

        final BlockProcessor blockProcessor = new BlockProcessor(blockProcessorContext);
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
            final MutableBlock mutableBlock = BlockTestUtil.createBlock();
            mutableBlock.setPreviousBlockHash(block02Hash);

            // Create a transaction that will be spent in the test's signed transaction.
            //  This transaction will create an output that can be spent by the test's private key.
            final Transaction transactionToSpend = TransactionTestUtil.createCoinbaseTransactionSpendableByPrivateKey(privateKey);
            mutableBlock.addTransaction(transactionToSpend);

            fakeBlock03 = mutableBlock;
        }

        final Transaction signedTransactionSpendingBlock03CoinbaseA;
        final Transaction signedTransactionSpendingBlock03CoinbaseB;
        {
            final AddressInflater addressInflater = new AddressInflater();

            final Transaction transactionToSpend = fakeBlock03.getCoinbaseTransaction();

            // Create an unsigned transaction that spends the test's previous transaction, and send the test's payment to an irrelevant address.
            final Transaction unsignedTransactionA;
            {
                final MutableTransaction mutableTransaction = TransactionTestUtil.createTransaction();

                final TransactionOutputIdentifier transactionOutputIdentifierToSpend = new TransactionOutputIdentifier(transactionToSpend.getHash(), 0);
                final TransactionInput transactionInput = TransactionTestUtil.createTransactionInput(transactionOutputIdentifierToSpend);
                mutableTransaction.addTransactionInput(transactionInput);

                final TransactionOutput transactionOutput = TransactionTestUtil.createTransactionOutput(
                    addressInflater.fromPrivateKey(PrivateKey.createNewKey(), false)
                );
                mutableTransaction.addTransactionOutput(transactionOutput);

                unsignedTransactionA = mutableTransaction;
            }

            // Create an unsigned transaction that spends the test's previous transaction, and send the test's payment to an irrelevant address.
            final Transaction unsignedTransactionB;
            {
                final MutableTransaction mutableTransaction = TransactionTestUtil.createTransaction();

                final TransactionOutputIdentifier transactionOutputIdentifierToSpend = new TransactionOutputIdentifier(transactionToSpend.getHash(), 0);
                final TransactionInput transactionInput = TransactionTestUtil.createTransactionInput(transactionOutputIdentifierToSpend);
                mutableTransaction.addTransactionInput(transactionInput);

                final TransactionOutput transactionOutput = TransactionTestUtil.createTransactionOutput(
                    addressInflater.fromPrivateKey(PrivateKey.createNewKey(), false)
                );
                mutableTransaction.addTransactionOutput(transactionOutput);

                unsignedTransactionB = mutableTransaction;
            }

            {
                final TransactionOutputRepository transactionOutputRepository = TransactionTestUtil.createTransactionOutputRepository(transactionToSpend);
                signedTransactionSpendingBlock03CoinbaseA = TransactionTestUtil.signTransaction(transactionOutputRepository, unsignedTransactionA, privateKey);
            }

            {
                final TransactionOutputRepository transactionOutputRepository = TransactionTestUtil.createTransactionOutputRepository(transactionToSpend);
                signedTransactionSpendingBlock03CoinbaseB = TransactionTestUtil.signTransaction(transactionOutputRepository, unsignedTransactionB, privateKey);
            }

            Assert.assertNotEquals(signedTransactionSpendingBlock03CoinbaseA.getHash(), signedTransactionSpendingBlock03CoinbaseB.getHash()); // Sanity check to ensure they are different transactions...

            { // Ensure the transactions would normally be valid on their own...
                final FakeUnspentTransactionOutputContext unspentTransactionOutputContext = new FakeUnspentTransactionOutputContext();
                unspentTransactionOutputContext.addTransaction(transactionToSpend, null, 4L, false);

                final TransactionValidatorContext transactionValidatorContext = new TransactionValidatorContext(transactionInflaters, new MutableNetworkTime(), FakeStaticMedianBlockTimeContext.MAX_MEDIAN_BLOCK_TIME, unspentTransactionOutputContext, upgradeSchedule);
                final TransactionValidator transactionValidator = new TransactionValidatorCore(transactionValidatorContext);

                {
                    final TransactionValidationResult transactionValidationResult = transactionValidator.validateTransaction(4L, signedTransactionSpendingBlock03CoinbaseA);
                    Assert.assertTrue(transactionValidationResult.isValid);
                }

                {
                    final TransactionValidationResult transactionValidationResult = transactionValidator.validateTransaction(4L, signedTransactionSpendingBlock03CoinbaseB);
                    Assert.assertTrue(transactionValidationResult.isValid);
                }
            }
        }

        final Block fakeBlock04;
        { // Spend the coinbase twice from different transactions within the same block...
            final MutableBlock mutableBlock = BlockTestUtil.createBlock();
            mutableBlock.setPreviousBlockHash(block02Hash);

            final Transaction regularCoinbaseTransaction = TransactionTestUtil.createCoinbaseTransactionSpendableByPrivateKey(PrivateKey.createNewKey());

            mutableBlock.addTransaction(regularCoinbaseTransaction);
            mutableBlock.addTransaction(signedTransactionSpendingBlock03CoinbaseA);
            mutableBlock.addTransaction(signedTransactionSpendingBlock03CoinbaseB);

            fakeBlock04 = mutableBlock;
        }

        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockInflater blockInflater = _masterInflater.getBlockInflater();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();
            for (final String blockData : new String[]{ BlockData.MainChain.GENESIS_BLOCK, BlockData.MainChain.BLOCK_1, BlockData.MainChain.BLOCK_2 }) {
                final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));

                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockHeaderDatabaseManager.storeBlockHeader(block);
                }

                pendingBlockDatabaseManager.storeBlock(block);
            }

            for (final Block block : new Block[] { fakeBlock03, fakeBlock04 }) {
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockHeaderDatabaseManager.storeBlockHeader(block);
                }

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
            Assert.assertNotNull(blockHeaderDatabaseManager.getBlockIdAtHeight(mainBlockchainSegmentId, 3L));

            final BlockId block04Id = blockHeaderDatabaseManager.getBlockHeaderId(fakeBlock04.getHash());
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final Boolean block04WasValid = blockDatabaseManager.hasTransactions(block04Id);
            Assert.assertFalse(block04WasValid);

            { // Assert the Block03 coinbase is still in the UTXO set since Block04 was invalid.
                final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();

                final Transaction transaction = fakeBlock03.getCoinbaseTransaction();
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transaction.getHash(), 0);
                final TransactionOutput unspentTransactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(transactionOutputIdentifier);
                Assert.assertNotNull(unspentTransactionOutput);
            }
        }
    }
}
