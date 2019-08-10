package com.softwareverde.bitcoin.slp.validator;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.sync.AddressProcessor;
import com.softwareverde.bitcoin.server.module.node.sync.AddressProcessorTests;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class SlpTransactionValidatorTests extends IntegrationTest {

    @Test
    public void should_validate_slp_transactions() throws Exception {
        final AddressProcessor addressProcessor = new AddressProcessor(_fullNodeDatabaseManagerFactory);
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            // Setup
            final List<TransactionId> transactionIds = AddressProcessorTests.loadBvtTokens(databaseManager);

            final HashMap<Sha256Hash, Boolean> slpValidityMap = new HashMap<Sha256Hash, Boolean>();
            slpValidityMap.put(Sha256Hash.fromHexString("34DD2FE8F0C5BBA8FC4F280C3815C1E46C2F52404F00DA3067D7CE12962F2ED0"), true);
            slpValidityMap.put(Sha256Hash.fromHexString("97BB8FFE6DC71AC5B263F322056069CF398CDA2677E21951364F00D2D572E887"), true);
            slpValidityMap.put(Sha256Hash.fromHexString("16EA62D94AC142BAF93A6C44C5DC961883DC4D38B85F737ED5B7BB326707C647"), false);
            slpValidityMap.put(Sha256Hash.fromHexString("9BD457D106B1EECBD43CD6ECA0A993420ABE16075B05012C8A76BB96D1AE16CE"), false);
            slpValidityMap.put(Sha256Hash.fromHexString("8572AA67141E5FB6C48557508D036542AAD99C828F22B429612BDCABBAD95373"), true);
            slpValidityMap.put(Sha256Hash.fromHexString("68092D36527D174CEA76797B3BB2677F61945FDECA01710976BF840664F7B71A"), true);
            slpValidityMap.put(Sha256Hash.fromHexString("0F58E80BF3E747E32BCF3218D77DC01495622D723589D1F1D1FD98AEFA798D3D"), true);
            slpValidityMap.put(Sha256Hash.fromHexString("4C27492AA05C9D4248ADF3DA47A9915FB0694D00D01462FF48B461E36486DE99"), true);
            slpValidityMap.put(Sha256Hash.fromHexString("87B17979CC05E9E5F5FA9E8C6D78482478A4E6F6D78360E818E16311F7F157F0"), true);
            slpValidityMap.put(Sha256Hash.fromHexString("731B7493DCAF21A368F384D75AD820F73F72DE9479622B35EF935E5D5C9D6F0E"), true);
            slpValidityMap.put(Sha256Hash.fromHexString("AE0D9AE505E4B75619A376FA70F7C295245F8FD28F3B625FBEA19E26AB29A928"), true);
            slpValidityMap.put(Sha256Hash.fromHexString("08937051BA961330600D382A749262753B8A941E9E155BA9798D2922C2CE3842"), false);
            slpValidityMap.put(Sha256Hash.fromHexString("9DF13E226887F408207F94E99108706B55149AF8C8EB9D2F36427BA3007DCD64"), false);
            slpValidityMap.put(Sha256Hash.fromHexString("25039E1E154AD0D0ED632AF5A6524898540EE8B310B878045343E8D93D7B88C1"), false);
            slpValidityMap.put(Sha256Hash.fromHexString("19DE9FFBBBCFB68BED5810ADE0F9B0929DBEEB4A7AA1236021324267209BF478"), false);

            // Action
            addressProcessor.start();

            final int maxSleepCount = 10;
            int sleepCount = 0;
            while (addressProcessor.getStatusMonitor().getStatus() != SleepyService.Status.SLEEPING) {
                Thread.sleep(250L);
                sleepCount += 1;

                if (sleepCount >= maxSleepCount) { throw new RuntimeException("Test execution timeout exceeded."); }
            }

            final AtomicInteger validationCount = new AtomicInteger(0);
            final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final SlpTransactionValidator slpTransactionValidator = new SlpTransactionValidator(new SlpTransactionValidator.TransactionAccumulator() {
                @Override
                public Map<Sha256Hash, Transaction> getTransactions(final List<Sha256Hash> transactionHashes) {
                    try {
                        final HashMap<Sha256Hash, Transaction> transactions = new HashMap<Sha256Hash, Transaction>(transactionHashes.getSize());
                        for (final Sha256Hash transactionHash : transactionHashes) {
                            final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
                            final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                            transactions.put(transactionHash, transaction);
                        }
                        return transactions;
                    }
                    catch (final DatabaseException databaseException) {
                        Logger.info(databaseException);
                        return null;
                    }
                }
            });

            // Assert
            for (final TransactionId transactionId : transactionIds) {
                final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                final Sha256Hash transactionHash = transaction.getHash();

                try {
                    final Boolean isValid = slpTransactionValidator.validateTransaction(transaction);
                    Logger.info(transactionHash + " " + (isValid != null ? "SLP" : "   ") + " " + (Util.coalesce(isValid, true) ? "VALID" : "INVALID"));
                    Assert.assertEquals(slpValidityMap.get(transactionHash), isValid);
                }
                finally {
                    synchronized (validationCount) {
                        validationCount.incrementAndGet();
                        validationCount.notify();
                    }
                }
            }

            synchronized (validationCount) {
                while (validationCount.get() < transactionIds.getSize()) {
                    validationCount.wait();
                }
            }
        }
        finally {
            addressProcessor.stop();
        }
    }
}
