package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.reader.ScriptReader;
import com.softwareverde.bitcoin.transaction.script.runner.Context;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.util.HexUtil;

public class PushOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_PUSH;

    protected static PushOperation fromScriptReader(final ScriptReader scriptReader) {
        if (! scriptReader.hasNextByte()) { return null; }

        final byte opcodeByte = scriptReader.getNextByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final SubType subType = TYPE.getSubtype(opcodeByte);
        if (subType == null) { return null; }

        final Value value;
        switch (subType) {
            // Pushes the literal value -1 to the stack.
            case PUSH_NEGATIVE_ONE: {
                value = Value.fromInteger(-1);
            } break;

            // Pushes the literal value 0 to the stack.
            case PUSH_ZERO: {
                value = Value.fromInteger(0);
            } break;

            // Interprets the opcode's value as an integer offset.  Then its value (+1) is pushed to the stack.
            //  (i.e. the literals 1-16)
            case PUSH_VALUE: {
                final int offset = (ByteUtil.byteToInteger(opcodeByte) - SubType.PUSH_VALUE.getMinValue());
                final int pushedValue = (offset + 1);
                value = Value.fromInteger(pushedValue);
            } break;

            // Interprets the opcode's value as an integer ("N").  Then, the next N bytes are pushed to the stack.
            case PUSH_DATA: {
                final int byteCount = ByteUtil.byteToInteger(opcodeByte);
                value = Value.fromBytes(scriptReader.getNextBytes(byteCount));
            } break;

            // Interprets the next byte as an integer ("N").  Then, the next N bytes are pushed to the stack.
            case PUSH_DATA_BYTE: {
                final int byteCount = ByteUtil.byteToInteger(scriptReader.getNextByte());
                value = Value.fromBytes(scriptReader.getNextBytes(byteCount));
            } break;

            // Interprets the next 2 bytes as an integer ("N").  Then, the next N bytes are pushed to the stack.
            case PUSH_DATA_SHORT: {
                final int byteCount = ByteUtil.bytesToInteger(scriptReader.getNextBytes(2));
                value = Value.fromBytes(scriptReader.getNextBytes(byteCount));
            } break;

            // Interprets the next 4 bytes as an integer ("N").  Then, the next N bytes are pushed to the stack.
            case PUSH_DATA_INTEGER: {
                final int byteCount = ByteUtil.bytesToInteger(scriptReader.getNextBytes(4));
                value = Value.fromBytes(scriptReader.getNextBytes(byteCount));
            } break;

            default: { return null; }
        }

        if (scriptReader.didOverflow()) { return null; }

        return new PushOperation(opcodeByte, subType, value);
    }

    protected final Value _value;

    protected PushOperation(final byte opcodeByte, final SubType subType, final Value value) {
        super(opcodeByte, TYPE, subType);
        _value = value;
    }

    public Value getValue() {
        return _value;
    }

    @Override
    public Boolean applyTo(final Stack stack, final Context context) {
        stack.push(_value);
        return true;
    }

    @Override
    public boolean equals(final Object object) {
        final Boolean superEquals = super.equals(object);
        if (! superEquals) { return false; }

        if (! (object instanceof PushOperation)) { return false ;}
        if (! super.equals(object)) { return false; }

        final PushOperation operation = (PushOperation) object;
        if (! (_value.equals(operation._value))) { return false; }

        return true;
    }

    @Override
    public String toString() {
        return (super.toString() + " Value: " + HexUtil.toHexString(_value.getBytes()));
    }
}
