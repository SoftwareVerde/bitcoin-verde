package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;

public class PushOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_PUSH;
    public static final Integer VALUE_MAX_BYTE_COUNT = 520; // Values should not be larger than 520 bytes in size. https://github.com/bitcoin/bitcoin/blob/v0.10.0rc3/src/script/script.h#L18

    protected static PushOperation fromBytes(final ByteArrayReader byteArrayReader) {
        if (! byteArrayReader.hasBytes()) { return null; }

        final byte opcodeByte = byteArrayReader.readByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final Opcode opcode = TYPE.getSubtype(opcodeByte);
        if (opcode == null) { return null; }

        final Boolean shouldSerializeValue;
        final Value value;
        switch (opcode) {
            // Pushes the literal value -1 to the stack.
            case PUSH_NEGATIVE_ONE: {
                value = Value.fromInteger(-1L);
                shouldSerializeValue = false;
            } break;

            // Pushes the literal value 0 to the stack.
            case PUSH_ZERO: {
                value = Value.fromInteger(0L);
                shouldSerializeValue = false;
            } break;

            // Interprets the opcode's value as an integer offset.  Then its value (+1) is pushed to the stack.
            //  (i.e. the literals 1-16)
            case PUSH_VALUE: {
                final int offset = (ByteUtil.byteToInteger(opcodeByte) - Opcode.PUSH_VALUE.getMinValue());
                final long pushedValue = (offset + 1);
                value = Value.fromInteger(pushedValue);
                shouldSerializeValue = false;
            } break;

            // Interprets the opcode's value as an integer ("N").  Then, the next N bytes are pushed to the stack.
            case PUSH_DATA: {
                final int byteCount = ByteUtil.byteToInteger(opcodeByte);
                value = Value.fromBytes(byteArrayReader.readBytes(byteCount));
                if (value == null) { return null; }

                shouldSerializeValue = true;
            } break;

            // Interprets the next byte as an integer ("N").  Then, the next N bytes are pushed to the stack.
            case PUSH_DATA_BYTE: {
                final int byteCount = byteArrayReader.readInteger(1);
                value = Value.fromBytes(byteArrayReader.readBytes(byteCount));
                if (value == null) { return null; }

                shouldSerializeValue = true;
            } break;

            // Interprets the next 2 bytes as an integer ("N").  Then, the next N bytes are pushed to the stack.
            case PUSH_DATA_SHORT: {
                final int byteCount = byteArrayReader.readInteger(2, Endian.LITTLE);
                if (byteCount > VALUE_MAX_BYTE_COUNT) {
                    Logger.log(opcode + " - Maximum byte count exceeded: " + byteCount);
                    return null; // It seems that enabling this restriction diminishes the usefulness of PUSH_DATA_INTEGER vs PUSH_DATA_SHORT...
                }

                value = Value.fromBytes(byteArrayReader.readBytes(byteCount));
                if (value == null) { return null; }

                shouldSerializeValue = true;
            } break;

            // Interprets the next 4 bytes as an integer ("N").  Then, the next N bytes are pushed to the stack.
            case PUSH_DATA_INTEGER: {
                final int byteCount = byteArrayReader.readInteger(4, Endian.LITTLE);
                if (byteCount > VALUE_MAX_BYTE_COUNT) {
                    Logger.log(opcode + " - Maximum byte count exceeded: " + byteCount);
                    return null; // It seems that enabling this restriction diminishes the usefulness of PUSH_DATA_INTEGER vs PUSH_DATA_SHORT...
                }

                value = Value.fromBytes(byteArrayReader.readBytes(byteCount));
                if (value == null) { return null; }

                shouldSerializeValue = true;
            } break;

            default: { return null; }
        }

        if (byteArrayReader.didOverflow()) { return null; }

        return new PushOperation(opcodeByte, opcode, value, shouldSerializeValue);
    }

    protected final Value _value;
    protected final Boolean _shouldSerializeValue;

    protected PushOperation(final byte opcodeByte, final Opcode opcode, final Value value, final Boolean shouldSerializeValue) {
        super(opcodeByte, TYPE, opcode);
        _value = value;
        _shouldSerializeValue = shouldSerializeValue;
    }

    public Value getValue() {
        return _value;
    }

    public Boolean containsBytes(final ByteArray byteArray) {
        return ByteUtil.areEqual(_value.getBytes(), byteArray.getBytes());
    }

    @Override
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableContext context) {
        stack.push(_value);
        return true;
    }

    @Override
    public byte[] getBytes() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendByte(_opcodeByte);
        if (_shouldSerializeValue) {
            byteArrayBuilder.appendBytes(_value.getBytes());
        }
        return byteArrayBuilder.build();
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

    @Override
    public String toStandardString() {
        if (_shouldSerializeValue) {
            return (super.toStandardString() + " 0x" + HexUtil.toHexString(_value.getBytes()));
        }
        else {
            return super.toStandardString();
        }
    }
}
