package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.util.ByteUtil;

import java.util.List;

public class ValueOperation extends Operation {
    public static final Type TYPE = Type.OP_VALUE;

    public static ValueOperation fromScript(final Script script) {
        if (! script.hasNextByte()) { return null; }

        final byte opcodeByte = script.getNextByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final SubType subType = TYPE.getSubtype(opcodeByte);
        if (subType == null) { return null; }

        final byte[] value;
        switch (subType) {
            // Pushes the literal value -1 to the stack.
            case PUSH_NEGATIVE_ONE: {
                value = ByteUtil.integerToBytes(-1);
            } break;

            // Pushes the literal value 0 to the stack.
            case PUSH_ZERO: {
                value = ByteUtil.integerToBytes(0);
            } break;

            // Interprets the opcode's value as an integer offset.  Then its value (+1) is pushed to the stack.
            //  (i.e. the literals 1-16)
            case PUSH_VALUE: {
                final int offset = (ByteUtil.byteToInteger(opcodeByte) - SubType.PUSH_VALUE.getMinValue());
                final int pushedValue = (offset + 1);
                value = ByteUtil.integerToBytes(pushedValue);
            } break;

            // Interprets the opcode's value as an integer ("N").  Then, the next N bytes are pushed to the stack.
            case PUSH_DATA: {
                final int byteCount = ByteUtil.byteToInteger(opcodeByte);
                value = script.getNextBytes(byteCount);
            } break;

            // Interprets the next byte as an integer ("N").  Then, the next N bytes are pushed to the stack.
            case PUSH_DATA_BYTE: {
                final int byteCount = ByteUtil.byteToInteger(script.getNextByte());
                value = script.getNextBytes(byteCount);
            } break;

            // Interprets the next 2 bytes as an integer ("N").  Then, the next N bytes are pushed to the stack.
            case PUSH_DATA_SHORT: {
                final int byteCount = ByteUtil.bytesToInteger(script.getNextBytes(2));
                value = script.getNextBytes(byteCount);
            } break;

            // Interprets the next 4 bytes as an integer ("N").  Then, the next N bytes are pushed to the stack.
            case PUSH_DATA_INTEGER: {
                final int byteCount = ByteUtil.bytesToInteger(script.getNextBytes(4));
                value = script.getNextBytes(byteCount);
            } break;

            default: { return null; }
        }

        return new ValueOperation(opcodeByte, value);
    }

    protected final byte[] _value;

    protected ValueOperation(final byte b, final byte[] value) {
        super(b, TYPE);
        _value = ByteUtil.copyBytes(value);
    }

    public ValueOperation(final byte[] value) {
        super(null, null);
        _value = ByteUtil.copyBytes(value);
    }

    public byte[] getValue() {
        return ByteUtil.copyBytes(_value);
    }

    @Override
    public void applyTo(final List<Operation> stack) {

    }
}
