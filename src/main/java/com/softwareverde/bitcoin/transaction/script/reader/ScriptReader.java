package com.softwareverde.bitcoin.transaction.script.reader;

import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.OperationInflater;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;

public class ScriptReader {
    public static List<Operation> getOperationList(final Script script) {
        final OperationInflater operationInflater = new OperationInflater();
        final ImmutableListBuilder<Operation> immutableListBuilder = new ImmutableListBuilder<Operation>();
        final ScriptReader scriptReader = new ScriptReader(script);
        while (scriptReader.hasNextByte()) {
            final int scriptPosition = scriptReader.getPosition();
            final Operation opcode = operationInflater.fromScriptReader(scriptReader);
            if (opcode == null) {
                scriptReader.setPosition(scriptPosition);
                Logger.log("NOTICE: Unknown opcode. 0x"+ HexUtil.toHexString(new byte[] { scriptReader.peakNextByte() }));
                return null;
            }

            immutableListBuilder.add(opcode);
        }
        return immutableListBuilder.build();
    }

    public static String toString(final Script script) {
        final List<Operation> scriptOperations = getOperationList(script);
        if (scriptOperations == null) { return null; }

        final StringBuilder stringBuilder = new StringBuilder();
        for (int i=0; i<scriptOperations.getSize(); ++i) {
            final Operation operation = scriptOperations.get(i);
            stringBuilder.append("(");
            stringBuilder.append(operation.toString());
            stringBuilder.append(")");

            if (i + 1 < scriptOperations.getSize()) {
                stringBuilder.append(" ");
            }
        }
        return stringBuilder.toString();
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

    public int getPosition() {
        return _index;
    }

    public void setPosition(final int position) {
        _index = position;
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
