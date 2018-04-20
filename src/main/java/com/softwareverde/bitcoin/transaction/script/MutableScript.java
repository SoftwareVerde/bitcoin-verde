package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.reader.ScriptReader;
import com.softwareverde.bitcoin.type.bytearray.overflow.MutableOverflowingByteArray;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.MutableHash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;

public class MutableScript implements Script {

    protected final MutableList<Operation> _operations = new MutableList<Operation>();

    public MutableScript() { }

    public MutableScript(final ByteArray byteArray) {
        ScriptReader.getOperationList(byteArray);
    }

    public void subScript(final int opcodeIndex) {
        if (opcodeIndex == 0) { return; }

        final int newByteCount = (_bytes.length - opcodeIndex);

        if (newByteCount > 0) {
            _bytes = ByteUtil.copyBytes(_bytes, opcodeIndex, newByteCount);
        }
        else {
            _bytes = new byte[0];
        }
    }

    public void removeOperations(final Operation.SubType operationType) {
final byte[] beforeBytes = ByteUtil.copyBytes(_bytes);
        final List<Operation> operationList = ScriptReader.getOperationList(this);
        final MutableList<Operation> retainedOperations = new MutableList<Operation>(operationList.getSize());
        for (final Operation operation : operationList) {
            final boolean isOperationType = operationType.matchesByte(operation.getOpcodeByte());
            if (! isOperationType) {
                retainedOperations.add(operation);
            }
        }

        final int matchCount = (operationList.getSize() - retainedOperations.getSize());
        if (matchCount == 0) { return; } // NOTE: The most common case...

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        for (final Operation operation : retainedOperations) {
            byteArrayBuilder.appendBytes(operation.getBytes());
        }
        _bytes = byteArrayBuilder.build();
final byte[] afterBytes = ByteUtil.copyBytes(_bytes);
if (! ByteUtil.areEqual(beforeBytes, afterBytes)) {
    Logger.log("Remove Operations: " + operationType.getValue());
    Logger.log(beforeBytes);
    Logger.log(afterBytes);
}
    }

    @Override
    public Hash getHash() {
        final byte[] hashBytes = BitcoinUtil.ripemd160(BitcoinUtil.sha256(_bytes));
        return MutableHash.wrap(hashBytes);
    }

    @Override
    public byte[] getBytes() {
        return new byte[0];
    }

    @Override
    public ImmutableScript asConst() {
        return new ImmutableScript(_bytes);
    }

    @Override
    public Json toJson() {
        final ScriptDeflater scriptDeflater = new ScriptDeflater();
        return scriptDeflater.toJson(this);
    }
}
