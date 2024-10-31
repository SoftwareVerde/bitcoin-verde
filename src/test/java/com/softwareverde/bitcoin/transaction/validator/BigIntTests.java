package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.bip.CoreUpgradeSchedule;
import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.test.fake.FakeUpgradeSchedule;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInputDeflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputDeflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputInflater;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.json.Json;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.bytearray.ByteArrayReader;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BigIntTests extends UnitTest {
    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception {
        super.after();
    }

    public boolean runVmbTestVectors(final Json testVectorsJson, final UpgradeSchedule upgradeSchedule, final Boolean expectedValue) {
        final TransactionInflater transactionInflater = new TransactionInflater();
        final TransactionDeflater transactionDeflater = new TransactionDeflater();
        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final TransactionOutputDeflater transactionOutputDeflater = new TransactionOutputDeflater();
        final TransactionInputDeflater transactionInputDeflater = new TransactionInputDeflater();

        Assert.assertTrue(testVectorsJson.length() > 0);

        int failedVectorCount = 0;
        final MutableList<String> failedTestIdentifiers = new MutableArrayList<>();
        for (int i = 0; i < testVectorsJson.length(); ++i) {
            final Json testJson = testVectorsJson.get(i);
            final String identifier = testJson.getString(0);
            // final String description = testJson.getString(1);
            // final String unlockingScriptDisassembled = testJson.getString(2);
            // final String lockingScriptDisassembled = testJson.getString(3);
            final String transactionToTestHex = testJson.getString(4);
            final String utxosToSpendHex = testJson.getString(5);
            // final Integer primaryInputIndex = testJson.getInteger(6);

            final Transaction transaction = transactionInflater.fromBytes(ByteArray.fromHexString(transactionToTestHex));
            final MutableList<TransactionOutput> outputsToSpend = new MutableArrayList<>();
            {
                final ByteArrayReader utxoStream = new ByteArrayReader(ByteArray.fromHexString(utxosToSpendHex));
                final int outputCount = CompactVariableLengthInteger.readVariableLengthInteger(utxoStream).intValue();
                for (int index = 0; index < outputCount; ++index) {
                    final TransactionInput transactionInput = transaction.getTransactionInputs().get(index);
                    final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(transactionInput.getPreviousOutputIndex(), utxoStream);
                    if (transactionOutput == null) { break; }
                    outputsToSpend.add(transactionOutput);
                }
            }

            int transactionInputIndex = 0;
            int invalidOutputCount = 0;
            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                final TransactionOutput transactionOutputToSpend = outputsToSpend.get(transactionInputIndex);
                final HistoricTransactionsTests.TestConfig testConfig = new HistoricTransactionsTests.TestConfig();
                testConfig.transactionBytes = transactionDeflater.toBytes(transaction).toString();
                testConfig.transactionInputBytes = transactionInputDeflater.toBytes(transactionInput).toString();
                testConfig.transactionOutputIndex = transactionInput.getPreviousOutputIndex();
                testConfig.transactionOutputBytes = transactionOutputDeflater.toBytes(transactionOutputToSpend).toString();
                testConfig.blockHeight = 900000L;
                testConfig.transactionInputIndex = transactionInputIndex;
                testConfig.lockingScriptBytes = transactionOutputToSpend.getLockingScript().toString();
                testConfig.unlockingScriptBytes = transactionInput.getUnlockingScript().toString();

                final Boolean isUnlocked = HistoricTransactionsTests.runScripts(testConfig, outputsToSpend, upgradeSchedule);
                if (! isUnlocked) {
                    invalidOutputCount += 1;
                    break;
                }

                transactionInputIndex += 1;
            }

            final boolean didFail = (expectedValue && invalidOutputCount > 0) || ((! expectedValue) && invalidOutputCount < 1);
            if (didFail) {
                failedVectorCount += 1;
                failedTestIdentifiers.add(identifier);
            }
        }

        if (failedVectorCount > 0) {
            final int vectorCount = testVectorsJson.length();
            System.out.println("Failed " + failedVectorCount + " of " + vectorCount + " vectors.");
            for (final String failedTestIdentifier : failedTestIdentifiers) {
                // System.out.println("Failed: " + failedTestIdentifier);
            }
        }

        // Assert.assertTrue(failedTestIdentifiers.isEmpty());
        return failedVectorCount == 0;
    }

    @Test
    public void run_all_vmb_tests() throws Exception {
        int failCount = 0;

        Logger.setLogLevel("com.softwareverde.bitcoin.transaction.script.runner", LogLevel.OFF); // TODO

        final Json manifest = Json.parse(IoUtil.getResource("/vmb_tests/manifest.json"));
        final Json testNamesJson = manifest.get("tests");
        for (int i = 0; i < testNamesJson.length(); ++i) {
            final String resourcePath = "/vmb_tests" + testNamesJson.getString(i);

            final Boolean isPreActivation = (! resourcePath.contains("bch_2025_"));
            final Boolean isValid = ((! resourcePath.contains("_invalid/")) && isPreActivation);

            if (! isPreActivation) { continue; } // TODO

            final FakeUpgradeSchedule upgradeSchedule = new FakeUpgradeSchedule(new CoreUpgradeSchedule());
            upgradeSchedule.setAreIntrospectionOperationsEnabled(true);
            upgradeSchedule.setAre64BitScriptIntegersEnabled(true);
            upgradeSchedule.setMultiplyOperationEnabled(true);
            upgradeSchedule.setSha256PayToScriptHashEnabled(true);
            upgradeSchedule.setLegacyPayToScriptHashEnabled(true);
            upgradeSchedule.setCashTokensEnabled(true);

            final Json testVectorsJson = Json.parse(IoUtil.getResource(resourcePath));

            boolean didPass = false;
            try {
                didPass = this.runVmbTestVectors(testVectorsJson, upgradeSchedule, isValid);
            }
            catch (final Exception exception) {
                // Nothing.
            }

            if (! didPass) {
                failCount += 1;
                System.out.println(resourcePath + ": FAIL");
            }
        }

        final int vectorCount = testNamesJson.length();
        System.out.println("Failed: " + failCount + " of " + vectorCount + ".");
        Assert.assertEquals(0, failCount);
    }
}
