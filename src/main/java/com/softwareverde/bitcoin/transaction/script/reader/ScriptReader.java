package com.softwareverde.bitcoin.transaction.script.reader;

import com.softwareverde.bitcoin.transaction.script.opcode.OperationInflater;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;

public class ScriptReader {
    public static List<Operation> getOperationList(final Script script) {
        final OperationInflater operationInflater = new OperationInflater();
        final ImmutableListBuilder<Operation> immutableListBuilder = new ImmutableListBuilder<Operation>();
        final ScriptReader scriptReader = new ScriptReader(script);
        while (scriptReader.hasNextByte()) {
            final Operation opcode = operationInflater.fromScriptReader(scriptReader);
            immutableListBuilder.add(opcode);
        }
        return immutableListBuilder.build();
    }

    protected final Script _script;
    protected int _index = 0;
    protected boolean _didOverflow = false;

    protected byte _getNextByte(final Boolean shouldConsumeByte) {
        if (_index >= _script.getByteCount()) {
            _didOverflow = true;
            return 0x00;
        }

        final byte b = _script.getByte(_index);

        if (shouldConsumeByte) {
            _index += 1;
        }

        return b;
    }

    public ScriptReader(final Script script) {
        _script = script;
    }

    public byte peakNextByte() {
        return _getNextByte(false);
    }

    public byte getNextByte() {
        return _getNextByte(true);
    }

    public byte[] getNextBytes(final int byteCount) {
        final byte[] bytes = new byte[byteCount];
        for (int i=0; i<byteCount; ++i) {
            bytes[i] = _getNextByte(true);
        }
        return bytes;
    }

    public boolean hasNextByte() {
        return (_index < _script.getByteCount());
    }

    public void resetPosition() {
        _index = 0;
    }

    public int getByteCount() {
        return _script.getByteCount();
    }

    public Boolean didOverflow() {
        return _didOverflow;
    }

    public Script getScript() {
        return _script;
    }
}
