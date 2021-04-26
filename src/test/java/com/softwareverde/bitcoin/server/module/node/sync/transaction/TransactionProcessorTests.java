package com.softwareverde.bitcoin.server.module.node.sync.transaction;

import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.bip.CoreUpgradeSchedule;
import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.context.core.BlockProcessorContext;
import com.softwareverde.bitcoin.context.core.BlockchainBuilderContext;
import com.softwareverde.bitcoin.context.core.TransactionProcessorContext;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.inflater.TransactionInflaters;
import com.softwareverde.bitcoin.server.module.node.BlockProcessor;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.pending.PendingTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.sync.BlockchainBuilder;
import com.softwareverde.bitcoin.server.module.node.sync.BlockchainBuilderTests;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.test.MockBlockStore;
import com.softwareverde.bitcoin.test.util.BlockTestUtil;
import com.softwareverde.bitcoin.test.util.TransactionTestUtil;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.signer.HashMapTransactionOutputRepository;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.type.time.SystemTime;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TransactionProcessorTests extends IntegrationTest {
    protected static Long COINBASE_MATURITY = null;

    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void transaction_spending_output_spent_by_other_mempool_tx_should_be_invalid() throws Exception {
        // This test inserts MainChain's Genesis -> Block01 -> Block02, then creates a fake Block03 with a spendable coinbase.
        //  The test then creates two transactions spending Block03's coinbase, and queues both for processing into the mempool.
        //  Only one of the transactions should be added to the mempool.

        // Setup
        final SystemTime systemTime = new SystemTime();
        final BlockInflater blockInflater = _masterInflater.getBlockInflater();
        final TransactionInflaters transactionInflaters = _masterInflater;
        final AddressInflater addressInflater = new AddressInflater();
        final MockBlockStore blockStore = new MockBlockStore();
        final BlockchainBuilderTests.FakeBitcoinNodeManager bitcoinNodeManager = new BlockchainBuilderTests.FakeBitcoinNodeManager();
        final BlockInflaters blockInflaters = BlockchainBuilderTests.FAKE_BLOCK_INFLATERS;

        final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();
        final BlockProcessorContext blockProcessorContext = new BlockProcessorContext(blockInflaters, transactionInflaters, blockStore, _fullNodeDatabaseManagerFactory, new MutableNetworkTime(), _synchronizationStatus, _difficultyCalculatorFactory, _transactionValidatorFactory, upgradeSchedule);
        final BlockchainBuilderContext blockchainBuilderContext = new BlockchainBuilderContext(blockInflaters, _fullNodeDatabaseManagerFactory, bitcoinNodeManager, systemTime, _threadPool);

        final BlockProcessor blockProcessor = new BlockProcessor(blockProcessorContext);

        final Sha256Hash block02Hash;
        {
            final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_2));
            block02Hash = block.getHash();
        }

        final PrivateKey privateKey = PrivateKey.createNewKey();

        final Block fakeBlock03;
        {
            final MutableBlock mutableBlock = BlockTestUtil.createBlock();
            mutableBlock.setPreviousBlockHash(block02Hash);

            // Create a transaction that will be spent in the signed transaction.
            //  This transaction will create an output that can be spent by the private key.
            final Transaction transactionToSpend = TransactionTestUtil.createCoinbaseTransactionSpendableByPrivateKey(privateKey);
            mutableBlock.addTransaction(transactionToSpend);

            fakeBlock03 = mutableBlock;
        }

        final Transaction transactionToSpend = fakeBlock03.getCoinbaseTransaction();

        final Transaction signedTransaction0;
        {
            final Transaction unsignedTransaction;
            {
                final MutableTransaction mutableTransaction = TransactionTestUtil.createTransaction();

                final TransactionOutputIdentifier transactionOutputIdentifierToSpend = new TransactionOutputIdentifier(transactionToSpend.getHash(), 0);
                final TransactionInput transactionInput = TransactionTestUtil.createTransactionInput(transactionOutputIdentifierToSpend);
                mutableTransaction.addTransactionInput(transactionInput);

                final TransactionOutput transactionOutput = TransactionTestUtil.createTransactionOutput(addressInflater.fromBase58Check("149uLAy8vkn1Gm68t5NoLQtUqBtngjySLF", false));
                mutableTransaction.addTransactionOutput(transactionOutput);

                unsignedTransaction = mutableTransaction;
            }

            {
                final HashMapTransactionOutputRepository transactionOutputRepository = new HashMapTransactionOutputRepository();
                final List<TransactionOutput> transactionOutputsToSpend = transactionToSpend.getTransactionOutputs();
                transactionOutputRepository.put(new TransactionOutputIdentifier(transactionToSpend.getHash(), 0), transactionOutputsToSpend.get(0));

                signedTransaction0 = TransactionTestUtil.signTransaction(transactionOutputRepository, unsignedTransaction, privateKey);
            }
        }

        final Transaction signedTransaction1;
        {
            final Transaction unsignedTransaction;
            {
                final MutableTransaction mutableTransaction = TransactionTestUtil.createTransaction();

                final TransactionOutputIdentifier transactionOutputIdentifierToSpend = new TransactionOutputIdentifier(transactionToSpend.getHash(), 0);
                final TransactionInput transactionInput = TransactionTestUtil.createTransactionInput(transactionOutputIdentifierToSpend);
                mutableTransaction.addTransactionInput(transactionInput);

                final TransactionOutput transactionOutput = TransactionTestUtil.createTransactionOutput(addressInflater.fromBase58Check("12c6DSiU4Rq3P4ZxziKxzrL5LmMBrzjrJX", false));
                mutableTransaction.addTransactionOutput(transactionOutput);

                unsignedTransaction = mutableTransaction;
            }

            {
                final HashMapTransactionOutputRepository transactionOutputRepository = new HashMapTransactionOutputRepository();
                final List<TransactionOutput> transactionOutputsToSpend = transactionToSpend.getTransactionOutputs();
                transactionOutputRepository.put(new TransactionOutputIdentifier(transactionToSpend.getHash(), 0), transactionOutputsToSpend.get(0));

                signedTransaction1 = TransactionTestUtil.signTransaction(transactionOutputRepository, unsignedTransaction, privateKey);
            }
        }

        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            for (final String blockData : new String[]{ BlockData.MainChain.GENESIS_BLOCK, BlockData.MainChain.BLOCK_1, BlockData.MainChain.BLOCK_2 }) {
                final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockHeaderDatabaseManager.storeBlockHeader(block);
                }
                blockStore.storePendingBlock(block);
            }

            for (final Block block : new Block[] { fakeBlock03 }) {
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockHeaderDatabaseManager.storeBlockHeader(block);
                }
                blockStore.storePendingBlock(block);
            }

            final PendingTransactionDatabaseManager pendingTransactionDatabaseManager = databaseManager.getPendingTransactionDatabaseManager();
            pendingTransactionDatabaseManager.storeTransaction(signedTransaction0);
            pendingTransactionDatabaseManager.storeTransaction(signedTransaction1);
        }

        { // Store the prerequisite blocks which sets up the utxo set for the mempool...
            final BlockchainBuilder blockchainBuilder = new BlockchainBuilder(blockchainBuilderContext, blockProcessor, blockStore, BlockchainBuilderTests.FAKE_DOWNLOAD_STATUS_MONITOR);
            final BlockchainBuilder.StatusMonitor statusMonitor = blockchainBuilder.getStatusMonitor();
            blockchainBuilder.start();
            final int maxSleepCount = 10;
            int sleepCount = 0;
            do {
                Thread.sleep(250L);
                sleepCount += 1;

                if (sleepCount >= maxSleepCount) { throw new RuntimeException("Test execution timeout exceeded."); }
            } while (statusMonitor.getStatus() != SleepyService.Status.SLEEPING);
            blockchainBuilder.stop();
        }

        final MutableList<Transaction> processedTransactions = new MutableList<Transaction>();
        final TransactionProcessorContext transactionProcessorContext = new TransactionProcessorContext(transactionInflaters, _fullNodeDatabaseManagerFactory, new MutableNetworkTime(), new SystemTime(), _transactionValidatorFactory, upgradeSchedule, _threadPool);
        final TransactionProcessor transactionProcessor = new TransactionProcessor(transactionProcessorContext);
        transactionProcessor.setNewTransactionProcessedCallback(new TransactionProcessor.Callback() {
            @Override
            public void onNewTransactions(final List<Transaction> transactions) {
                processedTransactions.addAll(transactions);
            }
        });

        { // Action
            final TransactionProcessor.StatusMonitor statusMonitor = transactionProcessor.getStatusMonitor();
            transactionProcessor.start();
            final int maxSleepCount = 50;
            int sleepCount = 0;
            do {
                Thread.sleep(250L);
                sleepCount += 1;

                if (sleepCount >= maxSleepCount) { throw new RuntimeException("Test execution timeout exceeded."); }
            } while (statusMonitor.getStatus() != SleepyService.Status.SLEEPING);
            transactionProcessor.stop();
        }

        // Assert
        Assert.assertEquals(1, processedTransactions.getCount());
    }
}
