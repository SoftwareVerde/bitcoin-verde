package com.softwareverde.bitcoin.wallet;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.slp.send.MutableSlpSendScript;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.util.Tuple;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class WalletTests extends UnitTest {

    @Before
    public void before() throws Exception {
        super.before();
    }

    @After
    public void after() throws Exception {
        super.after();
    }

    protected MutableList<Tuple<String, Long>> _setupTuples() {
        final MutableList<Tuple<String, Long>> sortedTuples = new MutableList<Tuple<String, Long>>();
        sortedTuples.add(new Tuple<String, Long>("One", 1L));
        sortedTuples.add(new Tuple<String, Long>("Two", 2L));
        sortedTuples.add(new Tuple<String, Long>("Three", 3L));
        sortedTuples.add(new Tuple<String, Long>("Four", 4L));
        sortedTuples.add(new Tuple<String, Long>("Five", 5L));
        sortedTuples.add(new Tuple<String, Long>("Six", 6L));
        sortedTuples.add(new Tuple<String, Long>("Seven", 7L));
        sortedTuples.add(new Tuple<String, Long>("Eight", 8L));
        sortedTuples.add(new Tuple<String, Long>("Nine", 9L));
        sortedTuples.add(new Tuple<String, Long>("Ten", 10L));
        return sortedTuples;
    }

    @Test
    public void should_select_closest_tuple_from_list_0() {
        // Setup
        final MutableList<Tuple<String, Long>> sortedTuples = _setupTuples();
        final Long desiredResult = 0L;

        // Action
        final Tuple<String, Long> selectedTuple = Wallet.removeClosestTupleAmount(sortedTuples, desiredResult);

        // Assert
        Assert.assertEquals(Long.valueOf(1L), selectedTuple.second);
        Assert.assertEquals(9, sortedTuples.getCount());
    }

    @Test
    public void should_select_closest_tuple_from_list_6() {
        // Setup
        final MutableList<Tuple<String, Long>> sortedTuples = _setupTuples();
        final Long desiredResult = 6L;

        // Action
        final Tuple<String, Long> selectedTuple = Wallet.removeClosestTupleAmount(sortedTuples, desiredResult);

        // Assert
        Assert.assertEquals(desiredResult, selectedTuple.second);
        Assert.assertEquals(9, sortedTuples.getCount());
    }

    @Test
    public void should_select_closest_tuple_from_list_7() {
        // Setup
        final MutableList<Tuple<String, Long>> sortedTuples = _setupTuples();
        final Long desiredResult = 7L;

        // Action
        final Tuple<String, Long> selectedTuple = Wallet.removeClosestTupleAmount(sortedTuples, desiredResult);

        // Assert
        Assert.assertEquals(desiredResult, selectedTuple.second);
        Assert.assertEquals(9, sortedTuples.getCount());
    }

    @Test
    public void should_select_closest_tuple_from_list_8() {
        // Setup
        final MutableList<Tuple<String, Long>> sortedTuples = _setupTuples();
        final Long desiredResult = 8L;

        // Action
        final Tuple<String, Long> selectedTuple = Wallet.removeClosestTupleAmount(sortedTuples, desiredResult);

        // Assert
        Assert.assertEquals(desiredResult, selectedTuple.second);
        Assert.assertEquals(9, sortedTuples.getCount());
    }

    @Test
    public void should_select_closest_tuple_from_list_20() {
        // Setup
        final MutableList<Tuple<String, Long>> sortedTuples = _setupTuples();
        final Long desiredResult = 20L;

        // Action
        final Tuple<String, Long> selectedTuple = Wallet.removeClosestTupleAmount(sortedTuples, desiredResult);

        // Assert
        Assert.assertEquals(Long.valueOf(10L), selectedTuple.second);
        Assert.assertEquals(9, sortedTuples.getCount());
    }

    @Test
    public void should_select_two_closest_tuples_from_list() {
        // Setup
        final MutableList<Tuple<String, Long>> sortedTuples = _setupTuples();

        // Action
        final Tuple<String, Long> selectedTuple0 = Wallet.removeClosestTupleAmount(sortedTuples, 5L);
        final Tuple<String, Long> selectedTuple1 = Wallet.removeClosestTupleAmount(sortedTuples, 5L);

        // Assert
        Assert.assertEquals(Long.valueOf(5L), selectedTuple0.second);
        Assert.assertEquals(Long.valueOf(6L), selectedTuple1.second);
        Assert.assertEquals(8, sortedTuples.getCount());
    }

    /**
     * This test handles an edge-case in which the the code used to override fee payment with a single sufficiently
     * large output does so with the knowledge of the value that is being provided by the mandatory inputs but then
     * when the total amount is checked at the end it was not calculated correctly and as a result returned null despite
     * having actually found a workable set of outputs to spend.
     */
    @Test
    public void should_use_output_funding_entire_transaction_even_if_mandatory_inputs_help() {
        // Setup
        final PrivateKey privateKey = PrivateKey.createNewKey();
        final AddressInflater addressInflater = new AddressInflater();
        final Address address = addressInflater.fromPrivateKey(privateKey, true);

        final SlpTokenId slpTokenId = SlpTokenId.wrap(HashUtil.sha256(new MutableByteArray(4)));

        final SlpScriptBuilder slpScriptBuilder = new SlpScriptBuilder();
        final MutableSlpSendScript slpSendScript = new MutableSlpSendScript();
        slpSendScript.setTokenId(slpTokenId);
        slpSendScript.setAmount(1, 50L);
        final LockingScript slpLockingScript = slpScriptBuilder.createSendScript(slpSendScript);

        final MutableTransactionOutput output0 = new MutableTransactionOutput();
        output0.setLockingScript(slpLockingScript);
        output0.setIndex(0);
        output0.setAmount(0L);
        output0.setLockingScript(slpLockingScript);

        final MutableTransactionOutput output1 = new MutableTransactionOutput();
        output1.setIndex(1);
        output1.setAmount(546L);
        output1.setLockingScript(ScriptBuilder.payToAddress(address));

        final MutableTransactionOutput output2 = new MutableTransactionOutput();
        output2.setIndex(2);
        output2.setAmount(1000L);
        output2.setLockingScript(ScriptBuilder.payToAddress(address));

        final MutableTransaction transaction = new MutableTransaction();
        transaction.addTransactionInput(new MutableTransactionInput());
        transaction.addTransactionOutput(output0);
        transaction.addTransactionOutput(output1);
        transaction.addTransactionOutput(output2);

        final Wallet wallet = new Wallet();
        wallet.addTransaction(transaction);
        wallet.addPrivateKey(privateKey);

        // Action
        final List<TransactionOutputIdentifier> transactionOutputList = wallet.getOutputsToSpend(3, 546L, slpTokenId, 10L);

        // Test
        Assert.assertNotNull(transactionOutputList);
        Assert.assertEquals(2, transactionOutputList.getCount());
    }

    @Test
    public void should_return_balance_of_watched_address() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final TransactionInflater transactionInflater = new TransactionInflater();

        final Wallet wallet = new Wallet();

        final Address address = addressInflater.fromBase32Check("bitcoincash:qp9f4jsawff8ucf82rnflrpay9l72mmy3urwm4ehxq", true);
        wallet.addWatchedAddress(address);

        final Transaction transaction = transactionInflater.fromBytes(ByteArray.fromHexString("020000000166F94F6821132FDAD4C512299E5DA9B59516D06768DA585490B60DA461F018C7010000006B483045022100A6857058B1F4060247290571E18BD1CB73A83D1FE7246B22D71D2F3B1AE5F49202206333BF6CA6FABE635D268B837353A82BADEA3AFCBB218308748154D8121B57B54121032B6465889BD84B2D41D2C352A08BD82169A1412B66647BF49B5D00C76F5F4F94FFFFFFFF0275300000000000001976A9144A9ACA1D72527E612750E69F8C3D217FE56F648F88ACC3C9A500000000001976A914EEE66F5C70D0C7D22FE824C28C2CFB5699100BAC88AC00000000"));
        wallet.addTransaction(transaction);

        // Action
        final Long balance = wallet.getWatchedBalance(address);
        final Long totalBalance = wallet.getBalance();

        // Assert
        Assert.assertEquals(Long.valueOf(12405L), balance);
        Assert.assertEquals(Long.valueOf(0L), totalBalance);
    }
}
