package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.context.TransactionOutputIndexerContext;
import com.softwareverde.bitcoin.context.lazy.LazyTransactionOutputIndexerContext;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.indexer.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.slp.SlpTransactionDatabaseManager;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SlpTransactionProcessorTests extends IntegrationTest {
    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_index_slp_transaction_validation_results() throws Exception {
        // Setup
        final TransactionOutputIndexerContext transactionOutputIndexerContext = new LazyTransactionOutputIndexerContext(_fullNodeDatabaseManagerFactory);
        final TransactionOutputIndexer transactionOutputIndexer = new TransactionOutputIndexer(transactionOutputIndexerContext);

        final List<Transaction> bvtTransactions = TransactionOutputIndexerTests.inflateBitcoinVerdeTestTokens();
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            for (final Transaction transaction : bvtTransactions) {
                final Sha256Hash transactionHash = transaction.getHash();
                transactionDatabaseManager.storeUnconfirmedTransaction(transaction);

                final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
                transactionDatabaseManager.addToUnconfirmedTransactions(transactionId);

                final TransactionOutputDatabaseManager transactionOutputDatabaseManager = databaseManager.getTransactionOutputDatabaseManager();
                transactionOutputDatabaseManager.queueTransactionsForProcessing(new ImmutableList<TransactionId>(transactionId));
            }
        }

        final SlpTransactionProcessor slpTransactionProcessor = new SlpTransactionProcessor(_fullNodeDatabaseManagerFactory);

        // Index the outputs so the SLP indexer can reference them...
        try {
            final TransactionOutputIndexer.StatusMonitor statusMonitor = transactionOutputIndexer.getStatusMonitor();
            transactionOutputIndexer.start();

            final int maxSleepCount = 10;
            int sleepCount = 0;
            do {
                Thread.sleep(250L);
                sleepCount += 1;

                if (sleepCount >= maxSleepCount) { throw new RuntimeException("Test execution timeout exceeded."); }
            } while (statusMonitor.getStatus() != SleepyService.Status.SLEEPING);
        }
        finally {
            transactionOutputIndexer.stop();
        }

        // Action
        try {
            final TransactionOutputIndexer.StatusMonitor statusMonitor = slpTransactionProcessor.getStatusMonitor();
            slpTransactionProcessor.start();

            final int maxSleepCount = 10;
            int sleepCount = 0;
            do {
                Thread.sleep(250L);
                sleepCount += 1;

                if (sleepCount >= maxSleepCount) { throw new RuntimeException("Test execution timeout exceeded."); }
            } while (statusMonitor.getStatus() != SleepyService.Status.SLEEPING);
        }
        finally {
            slpTransactionProcessor.stop();
        }

        // Assert
        final List<TransactionOutputIdentifier> expectedSlpTransactionOutputIdentifiers;
        {
            final ImmutableListBuilder<TransactionOutputIdentifier> transactionOutputIdentifiers = new ImmutableListBuilder<TransactionOutputIdentifier>();
            transactionOutputIdentifiers.addAll(TransactionOutputIndexerTests.createOutputIdentifiers("34DD2FE8F0C5BBA8FC4F280C3815C1E46C2F52404F00DA3067D7CE12962F2ED0", new int[] { 0, 1, 2 }));
            transactionOutputIdentifiers.addAll(TransactionOutputIndexerTests.createOutputIdentifiers("97BB8FFE6DC71AC5B263F322056069CF398CDA2677E21951364F00D2D572E887", new int[] { 0, 1, 2 }));
            transactionOutputIdentifiers.addAll(TransactionOutputIndexerTests.createOutputIdentifiers("8572AA67141E5FB6C48557508D036542AAD99C828F22B429612BDCABBAD95373", new int[] { 0, 1, 2 }));
            transactionOutputIdentifiers.addAll(TransactionOutputIndexerTests.createOutputIdentifiers("68092D36527D174CEA76797B3BB2677F61945FDECA01710976BF840664F7B71A", new int[] { 0, 1 }));
            transactionOutputIdentifiers.addAll(TransactionOutputIndexerTests.createOutputIdentifiers("0F58E80BF3E747E32BCF3218D77DC01495622D723589D1F1D1FD98AEFA798D3D", new int[] { 0, 1, 2 }));
            transactionOutputIdentifiers.addAll(TransactionOutputIndexerTests.createOutputIdentifiers("4C27492AA05C9D4248ADF3DA47A9915FB0694D00D01462FF48B461E36486DE99", new int[] { 0, 1, 2, 3 }));
            transactionOutputIdentifiers.addAll(TransactionOutputIndexerTests.createOutputIdentifiers("87B17979CC05E9E5F5FA9E8C6D78482478A4E6F6D78360E818E16311F7F157F0", new int[] { 0, 1, 2 }));
            transactionOutputIdentifiers.addAll(TransactionOutputIndexerTests.createOutputIdentifiers("731B7493DCAF21A368F384D75AD820F73F72DE9479622B35EF935E5D5C9D6F0E", new int[] { 0, 1, 2 }));
            transactionOutputIdentifiers.addAll(TransactionOutputIndexerTests.createOutputIdentifiers("AE0D9AE505E4B75619A376FA70F7C295245F8FD28F3B625FBEA19E26AB29A928", new int[] { 0, 1, 2 }));
            transactionOutputIdentifiers.addAll(TransactionOutputIndexerTests.createOutputIdentifiers("08937051BA961330600D382A749262753B8A941E9E155BA9798D2922C2CE3842", new int[] { 0, 1 }));
            transactionOutputIdentifiers.addAll(TransactionOutputIndexerTests.createOutputIdentifiers("9DF13E226887F408207F94E99108706B55149AF8C8EB9D2F36427BA3007DCD64", new int[] { 0, 1 }));
            transactionOutputIdentifiers.addAll(TransactionOutputIndexerTests.createOutputIdentifiers("25039E1E154AD0D0ED632AF5A6524898540EE8B310B878045343E8D93D7B88C1", new int[] { 0, 1 }));
            transactionOutputIdentifiers.addAll(TransactionOutputIndexerTests.createOutputIdentifiers("19DE9FFBBBCFB68BED5810ADE0F9B0929DBEEB4A7AA1236021324267209BF478", new int[] { 0, 1 }));
            transactionOutputIdentifiers.addAll(TransactionOutputIndexerTests.createOutputIdentifiers("9BD457D106B1EECBD43CD6ECA0A993420ABE16075B05012C8A76BB96D1AE16CE", new int[] { 0, 1 }));
            expectedSlpTransactionOutputIdentifiers = transactionOutputIdentifiers.build();
        }

        final List<Sha256Hash> expectedInvalidSlpTransactions;
        {
            final ImmutableListBuilder<Sha256Hash> listBuilder = new ImmutableListBuilder<Sha256Hash>();
            listBuilder.add(Sha256Hash.fromHexString("9BD457D106B1EECBD43CD6ECA0A993420ABE16075B05012C8A76BB96D1AE16CE"));
            listBuilder.add(Sha256Hash.fromHexString("08937051BA961330600D382A749262753B8A941E9E155BA9798D2922C2CE3842"));
            listBuilder.add(Sha256Hash.fromHexString("9DF13E226887F408207F94E99108706B55149AF8C8EB9D2F36427BA3007DCD64"));
            listBuilder.add(Sha256Hash.fromHexString("25039E1E154AD0D0ED632AF5A6524898540EE8B310B878045343E8D93D7B88C1"));
            listBuilder.add(Sha256Hash.fromHexString("19DE9FFBBBCFB68BED5810ADE0F9B0929DBEEB4A7AA1236021324267209BF478"));
            expectedInvalidSlpTransactions = listBuilder.build();
        }

        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final SlpTransactionDatabaseManager slpTransactionDatabaseManager = databaseManager.getSlpTransactionDatabaseManager();

            for (final TransactionOutputIdentifier transactionOutputIdentifier : expectedSlpTransactionOutputIdentifiers) {
                final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);

                final Boolean expectedValidationResult = (! expectedInvalidSlpTransactions.contains(transactionHash));
                final Boolean isValidSlpTransaction = slpTransactionDatabaseManager.getSlpTransactionValidationResult(transactionId);

                Assert.assertEquals(expectedValidationResult, isValidSlpTransaction);
            }
        }

    }
}
