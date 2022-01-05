package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.bip.CoreUpgradeSchedule;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.test.fake.FakeUpgradeSchedule;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInputDeflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputDeflater;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.json.Json;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.Util;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;

public class NativeIntrospectionBchnTests extends UnitTest {
    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void run_test_vectors() {
        final TransactionInflater transactionInflater = new TransactionInflater();
        final TransactionDeflater transactionDeflater = new TransactionDeflater();
        final TransactionInputDeflater transactionInputDeflater = new TransactionInputDeflater();
        final TransactionOutputDeflater transactionOutputDeflater = new TransactionOutputDeflater();

        final FakeUpgradeSchedule fakeUpgradeSchedule = new FakeUpgradeSchedule(new CoreUpgradeSchedule());
        fakeUpgradeSchedule.setAreIntrospectionOperationsEnabled(true);
        fakeUpgradeSchedule.setAre64BitScriptIntegersEnabled(true);
        fakeUpgradeSchedule.setMultiplyOperationEnabled(true);

        final Json testVectorsJson = Json.parse(IoUtil.getResource("/bchn_introspection_vectors.json"));
        final HashMap<Sha256Hash, Transaction> transactionsToSpend = new HashMap<>();
        {
            final Json transactionsToSpendJson = testVectorsJson.get("transactionsToSpend");
            for (int i = 0; i < transactionsToSpendJson.length(); ++i) {
                final String transactionHex = transactionsToSpendJson.getString(i);
                final Transaction transaction = transactionInflater.fromBytes(ByteArray.fromHexString(transactionHex));
                transactionsToSpend.put(transaction.getHash(), transaction);
            }
        }

        final Json testsJson = testVectorsJson.get("tests");
        for (int i = 0; i < testsJson.length(); ++i) {
            final Json testJson = testsJson.get(i);
            final String comment = testJson.getString("comment");
            final Boolean expectedResult = Util.parseBool(testJson.getString("expectedResult"));
            final String transactionToSpendString = testJson.getString("fundingTransaction");
            final String transactionString = testJson.getString("transaction");

            final Transaction fundingTransaction = transactionInflater.fromBytes(ByteArray.fromHexString(transactionToSpendString));
            final Transaction transaction = transactionInflater.fromBytes(ByteArray.fromHexString(transactionString));

            transactionsToSpend.put(fundingTransaction.getHash(), fundingTransaction);
            transactionsToSpend.put(transaction.getHash(), transaction);

            System.out.print("Test " + i + ": " + comment + " Inputs: [");

            String separator = "";
            int invalidScriptCount = 0;
            int transactionInputIndex = 0;
            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                System.out.print(separator + transactionInputIndex);
                separator = " ";

                final Transaction transactionToSpend = transactionsToSpend.get(transactionInput.getPreviousOutputTransactionHash());
                final TransactionOutput transactionOutputToSpend = transactionToSpend.getTransactionOutputs().get(transactionInput.getPreviousOutputIndex());
                final HistoricTransactionsTests.TestConfig testConfig = new HistoricTransactionsTests.TestConfig();
                testConfig.transactionBytes = transactionDeflater.toBytes(transaction).toString();
                testConfig.transactionInputBytes = transactionInputDeflater.toBytes(transactionInput).toString();
                testConfig.transactionOutputIndex = transactionInput.getPreviousOutputIndex();
                testConfig.transactionOutputBytes = transactionOutputDeflater.toBytes(transactionOutputToSpend).toString();
                testConfig.blockHeight = 900000L;
                testConfig.transactionInputIndex = transactionInputIndex;
                testConfig.lockingScriptBytes = transactionOutputToSpend.getLockingScript().toString();
                testConfig.unlockingScriptBytes = transactionInput.getUnlockingScript().toString();

                final Boolean isUnlocked = HistoricTransactionsTests.runScripts(testConfig, transactionsToSpend, fakeUpgradeSchedule);
                if (! isUnlocked) {
                    invalidScriptCount += 1;
                }

                transactionInputIndex += 1;
            }
            if (expectedResult) {
                Assert.assertEquals(0, invalidScriptCount);
            }
            else {
                Assert.assertEquals(1, invalidScriptCount);
            }

            System.out.println("]");
        }
    }
}
