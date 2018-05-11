package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.OperationInflater;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class ScriptInflater {
    protected MutableList<Operation> _getOperationList(final ByteArray bytes) {
        final OperationInflater operationInflater = new OperationInflater();
        final MutableList<Operation> mutableList = new MutableList<Operation>();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        while (byteArrayReader.remainingByteCount() > 0) {
            final int scriptPosition = byteArrayReader.getPosition();
            final Operation opcode = operationInflater.fromBytes(byteArrayReader);
            if (opcode == null) {
                byteArrayReader.setPosition(scriptPosition);
                Logger.log("NOTICE: Unable to inflate opcode. 0x"+ HexUtil.toHexString(new byte[] { byteArrayReader.peakByte() }));
                return null;
            }

            mutableList.add(opcode);
        }
        return mutableList;
    }

    protected Script _fromByteArray(final ByteArray byteArray) {
        final List<Operation> operations = _getOperationList(byteArray);
        if (operations == null) { return null; }

        final MutableScript mutableScript = new MutableScript();
        mutableScript._operations.addAll(operations);
        mutableScript._cachedByteCount = byteArray.getByteCount();
        return mutableScript;
    }

    public Script fromBytes(final byte[] bytes) {
        final ByteArray byteArray = MutableByteArray.wrap(bytes);
        return _fromByteArray(byteArray);
    }

    public Script fromBytes(final ByteArray byteArray) {
        return _fromByteArray(byteArray);
    }
}
