package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

public class ScriptInflaterTests {
    @Test
    public void should_inflate_basic_unlocking_script() {
        // Setup
        final String unlockingScriptHexString = "47304402201AFBFFC4EA7D41F2D71CFC2F7DC2AFBA406819DA44D363CB1163EFF5DA2C96BA02201E46E79DF44D324086F233AC0B9C0675C82122F96F6837C9058588DDFB986E26014104751C030813B7624983C30AC932ACD2FEBAA9AA9337C27BF03B875B2466E859064ADA52820195A81D83CE0D28FD8BD217B4BBBDA1A76B256A46A9E5FDB8EA435A";
        final Script expectedScript;
        final ScriptBuilder scriptBuilder = new ScriptBuilder();
        scriptBuilder.pushBytes(MutableByteArray.wrap(HexUtil.hexStringToByteArray("304402201AFBFFC4EA7D41F2D71CFC2F7DC2AFBA406819DA44D363CB1163EFF5DA2C96BA02201E46E79DF44D324086F233AC0B9C0675C82122F96F6837C9058588DDFB986E2601")));
        scriptBuilder.pushBytes(MutableByteArray.wrap(HexUtil.hexStringToByteArray("04751C030813B7624983C30AC932ACD2FEBAA9AA9337C27BF03B875B2466E859064ADA52820195A81D83CE0D28FD8BD217B4BBBDA1A76B256A46A9E5FDB8EA435A")));
        expectedScript = scriptBuilder.build();

        final ScriptInflater scriptInflater = new ScriptInflater();

        // Action
        final Script script = scriptInflater.fromBytes(HexUtil.hexStringToByteArray(unlockingScriptHexString));

        // Assert
        TestUtil.assertEqual(expectedScript.getBytes().getBytes(), script.getBytes().getBytes());

        final List<Operation> operations = script.getOperations();
        Assert.assertEquals(2, operations.getSize());

        Assert.assertEquals(Operation.Type.OP_PUSH, operations.get(0).getType());
        Assert.assertEquals(Operation.Type.OP_PUSH, operations.get(1).getType());

        Assert.assertEquals(0x47, operations.get(0).getOpcodeByte());
        Assert.assertEquals(0x41, operations.get(1).getOpcodeByte());

        final PushOperation pushOperation0 = (PushOperation) operations.get(0);
        TestUtil.assertEqual(HexUtil.hexStringToByteArray("304402201AFBFFC4EA7D41F2D71CFC2F7DC2AFBA406819DA44D363CB1163EFF5DA2C96BA02201E46E79DF44D324086F233AC0B9C0675C82122F96F6837C9058588DDFB986E2601"), pushOperation0.getValue().getBytes());

        final PushOperation pushOperation1 = (PushOperation) operations.get(1);
        TestUtil.assertEqual(HexUtil.hexStringToByteArray("04751C030813B7624983C30AC932ACD2FEBAA9AA9337C27BF03B875B2466E859064ADA52820195A81D83CE0D28FD8BD217B4BBBDA1A76B256A46A9E5FDB8EA435A"), pushOperation1.getValue().getBytes());
    }
}
