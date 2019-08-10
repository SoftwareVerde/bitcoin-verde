package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.constable.list.List;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

public class ScriptBuilderTests {
    @Test
    public void should_recreate_pay_to_address_script() {
        // Setup
        final String address = "1ACFXTz7v92dK2ymZ5VDGQE5vZKhF82DVd";
        final TransactionInflater transactionInflater = new TransactionInflater();

        final byte[] expectedTransactionBytes = HexUtil.hexStringToByteArray("01000000019E4FC764B63B143FD65296E9163D193F4053459518939ECD608C0D1E29DC5F7F050000006B4830450221008D33C6CFA30A8F3DDA347CD307B7EB2BDBC8492057F377455DA70E298BE67ACB02203F5332199E677FC9646724C4A78C2A2A090B3732B848A0DD0995E5FBE0AC0B16012102D7C2342ED6EF6E9DB7FEC62E14358FFBDC661C42AE9EF34D7BD52413E89512B0FFFFFFFF010AD92800000000001976A91464D9D52CA7CE4D4B73919A7F078921A94ACDDBAB88AC00000000");
        final Transaction expectedTransaction = transactionInflater.fromBytes(expectedTransactionBytes);
        final TransactionOutput expectedTransactionOutput = expectedTransaction.getTransactionOutputs().get(0);
        final Script expectedLockingScript = expectedTransactionOutput.getLockingScript();

        final List<Operation> expectedOperations = expectedLockingScript.getOperations();

        // Action
        final Script lockingScript = ScriptBuilder.payToAddress(address);

        // Assert
        final List<Operation> operations = lockingScript.getOperations();

        for (int i = 0; i < operations.getSize(); ++i) {
            final Operation operation = operations.get(i);
            final Operation expectedOperation = expectedOperations.get(i);
            Assert.assertEquals(expectedOperation, operation);
        }

        TestUtil.assertEqual(expectedLockingScript.getBytes().getBytes(), lockingScript.getBytes().getBytes());
    }
}
