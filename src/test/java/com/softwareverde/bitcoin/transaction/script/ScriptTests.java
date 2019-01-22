package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.script.opcode.Opcode;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.constable.list.List;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

public class ScriptTests {
    @Test
    public void should_transform_mutable_unlocking_script_to_immutable_unlocking_script() {

        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("01000000024DE8B0C4C2582DB95FA6B3567A989B664484C7AD6672C85A3DA413773E63FDB8000000006B48304502205B282FBC9B064F3BC823A23EDCC0048CBB174754E7AA742E3C9F483EBE02911C022100E4B0B3A117D36CAB5A67404DDDBF43DB7BEA3C1530E0FE128EBC15621BD69A3B0121035AA98D5F77CD9A2D88710E6FC66212AFF820026F0DAD8F32D1F7CE87457DDE50FFFFFFFF4DE8B0C4C2582DB95FA6B3567A989B664484C7AD6672C85A3DA413773E63FDB8010000006F004730440220276D6DAD3DEFA37B5F81ADD3992D510D2F44A317FD85E04F93A1E2DAEA64660202200F862A0DA684249322CEB8ED842FB8C859C0CB94C81E1C5308B4868157A428EE01AB51210232ABDC893E7F0631364D7FD01CB33D24DA45329A00357B3A7886211AB414D55A51AEFFFFFFFF02E0FD1C00000000001976A914380CB3C594DE4E7E9B8E18DB182987BEBB5A4F7088ACC0C62D000000000017142A9BC5447D664C1D0141392A842D23DBA45C4F13B17500000000"));
        final TransactionInput transactionInput = transaction.getTransactionInputs().get(1);

        final MutableScript mutableUnlockingScript = new MutableScript(transactionInput.getUnlockingScript());
        mutableUnlockingScript.subScript(3);
        { // Sanity Checking...
            final List<Operation> operations = mutableUnlockingScript.getOperations();
            // (0x51 OP_PUSH-PUSH_VALUE Value: 01000000)
            Assert.assertEquals(1, ((PushOperation) operations.get(0)).getValue().asInteger().intValue());
            // (0x21 OP_PUSH-PUSH_DATA Value: 0232ABDC893E7F0631364D7FD01CB33D24DA45329A00357B3A7886211AB414D55A)
            TestUtil.assertEqual(HexUtil.hexStringToByteArray("0232ABDC893E7F0631364D7FD01CB33D24DA45329A00357B3A7886211AB414D55A"), ((PushOperation) operations.get(1)).getValue().getBytes());
            // (0x51 OP_PUSH-PUSH_VALUE Value: 01000000)
            Assert.assertEquals(1, ((PushOperation) operations.get(2)).getValue().asInteger().intValue());
            // (0xAE OP_CRYPTOGRAPHIC-CHECK_MULTISIGNATURE)
            Assert.assertTrue(Opcode.CHECK_MULTISIGNATURE.matchesByte(operations.get(3).getOpcodeByte()));
        }

        // Action
        final UnlockingScript unlockingScript = UnlockingScript.castFrom(mutableUnlockingScript);

        // Assert
        final List<Operation> operations = unlockingScript.getOperations();
        Assert.assertEquals(4, operations.getSize());
        // (0x51 OP_PUSH-PUSH_VALUE Value: 01000000)
        Assert.assertEquals(1, ((PushOperation) operations.get(0)).getValue().asInteger().intValue());
        // (0x21 OP_PUSH-PUSH_DATA Value: 0232ABDC893E7F0631364D7FD01CB33D24DA45329A00357B3A7886211AB414D55A)
        TestUtil.assertEqual(HexUtil.hexStringToByteArray("0232ABDC893E7F0631364D7FD01CB33D24DA45329A00357B3A7886211AB414D55A"), ((PushOperation) operations.get(1)).getValue().getBytes());
        // (0x51 OP_PUSH-PUSH_VALUE Value: 01000000)
        Assert.assertEquals(1, ((PushOperation) operations.get(2)).getValue().asInteger().intValue());
        // (0xAE OP_CRYPTOGRAPHIC-CHECK_MULTISIGNATURE)
        Assert.assertTrue(Opcode.CHECK_MULTISIGNATURE.matchesByte(operations.get(3).getOpcodeByte()));
    }
}
