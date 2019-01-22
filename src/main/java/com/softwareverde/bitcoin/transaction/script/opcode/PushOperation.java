package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.server.Constants;
import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class PushOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_PUSH;
    public static final Integer VALUE_MAX_BYTE_COUNT = 520; // NOTE: Values should not be larger than 520 bytes in size. https://github.com/bitcoin/bitcoin/blob/v0.10.0rc3/src/script/script.h#L18

    protected static class Payload {
        public final Boolean shouldBeSerialized;
        public final Integer valueByteCountLength; // The number of bytes that should be used when serializing the number of bytes within the Value. (Ex. 0x0001 -> 2, vs 0x00000001 -> 4)
        public final Value value;

        public Payload(final Boolean shouldBeSerialized, final Integer valueByteCountLength, final Value value) {
            this.shouldBeSerialized = shouldBeSerialized;
            this.valueByteCountLength = valueByteCountLength;
            this.value = value;
        }
    }

    protected static PushOperation fromBytes(final ByteArrayReader byteArrayReader) {
        if (! byteArrayReader.hasBytes()) { return null; }

        final byte opcodeByte = byteArrayReader.readByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final Opcode opcode = TYPE.getSubtype(opcodeByte);
        if (opcode == null) { return null; }

        final Payload payload;
        switch (opcode) {
            // Pushes the literal value -1 to the stack.
            case PUSH_NEGATIVE_ONE: {
                final Integer valueByteCountLength = null;
                final Value value = Value.fromInteger(-1L);
                payload = new Payload(false, valueByteCountLength, value);
            } break;

            // Pushes the literal value 0 to the stack.
            case PUSH_ZERO: {
                final Integer valueByteCountLength = null;
                final Value value = Value.fromInteger(0L);
                payload = new Payload(false, valueByteCountLength, value);
            } break;

            // Interprets the opcode's value as an integer offset.  Then its value (+1) is pushed to the stack.
            //  (i.e. the literals 1-16)
            case PUSH_VALUE: {
                final Integer valueByteCountLength = null;
                final int offset = (ByteUtil.byteToInteger(opcodeByte) - Opcode.PUSH_VALUE.getMinValue());
                final long pushedValue = (offset + 1);
                final Value value = Value.fromInteger(pushedValue);
                payload = new Payload(false, valueByteCountLength, value);
            } break;

            // Interprets the opcode's value as an integer ("N").  Then, the next N bytes are pushed to the stack.
            case PUSH_DATA: {
                final Integer valueByteCountLength = null;
                final int byteCount = ByteUtil.byteToInteger(opcodeByte);
                final Value value = Value.fromBytes(byteArrayReader.readBytes(byteCount));
                if (value == null) { return null; }
                payload = new Payload(true, valueByteCountLength, value);
            } break;

            // Interprets the next byte as an integer ("N").  Then, the next N bytes are pushed to the stack.
            case PUSH_DATA_BYTE: {
                final Integer valueByteCountLength = 1;
                final int byteCount = byteArrayReader.readInteger(valueByteCountLength);
                if (byteCount < 0) { return null; }
                if (byteCount > VALUE_MAX_BYTE_COUNT) {
                    // Logger.log(opcode + " - Maximum byte count exceeded: " + byteCount);
                    return null; // It seems that enabling this restriction diminishes the usefulness of PUSH_DATA_INTEGER vs PUSH_DATA_SHORT...
                }
                final Value value = Value.fromBytes(byteArrayReader.readBytes(byteCount));
                if (value == null) { return null; }
                payload = new Payload(true, valueByteCountLength, value);
            } break;

            // Interprets the next 2 bytes as an integer ("N").  Then, the next N bytes are pushed to the stack.
            case PUSH_DATA_SHORT: {
                final Integer valueByteCountLength = 2;
                final int byteCount = byteArrayReader.readInteger(valueByteCountLength, Endian.LITTLE);
                if (byteCount < 0) { return null; }
                if (byteCount > VALUE_MAX_BYTE_COUNT) {
                    // Logger.log(opcode + " - Maximum byte count exceeded: " + byteCount);
                    return null; // It seems that enabling this restriction diminishes the usefulness of PUSH_DATA_INTEGER vs PUSH_DATA_SHORT...
                }

                final Value value = Value.fromBytes(byteArrayReader.readBytes(byteCount));
                if (value == null) { return null; }
                payload = new Payload(true, valueByteCountLength, value);
            } break;

            // Interprets the next 4 bytes as an integer ("N").  Then, the next N bytes are pushed to the stack.
            case PUSH_DATA_INTEGER: {
                final Integer valueByteCountLength = 4;
                final int byteCount = byteArrayReader.readInteger(valueByteCountLength, Endian.LITTLE);
                if (byteCount < 0) { return null; }
                if (byteCount > VALUE_MAX_BYTE_COUNT) {
                    // Logger.log(opcode + " - Maximum byte count exceeded: " + byteCount);
                    return null; // It seems that enabling this restriction diminishes the usefulness of PUSH_DATA_INTEGER vs PUSH_DATA_SHORT...
                }

                final Value value = Value.fromBytes(byteArrayReader.readBytes(byteCount));
                if (value == null) { return null; }
                payload = new Payload(true, valueByteCountLength, value);
            } break;

            case PUSH_VERSION: {
                final Integer valueByteCountLength = null;
                final Value value = Value.fromBytes(StringUtil.stringToBytes(Constants.USER_AGENT));
                payload = new Payload(false, valueByteCountLength, value);
            } break;

            default: { return null; }
        }

        if (byteArrayReader.didOverflow()) { return null; }

        return new PushOperation(opcodeByte, opcode, payload);
    }

    protected final Payload _payload;

    protected PushOperation(final byte opcodeByte, final Opcode opcode, final Payload payload) {
        super(opcodeByte, TYPE, opcode);
        _payload = payload;
    }

    public Value getValue() {
        return _payload.value;
    }

    public Boolean containsBytes(final ByteArray byteArray) {
        return ByteUtil.areEqual(_payload.value.getBytes(), byteArray.getBytes());
    }

    @Override
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableContext context) {
        if (! _opcode.isEnabled()) {
            Logger.log("NOTICE: Opcode is disabled: " + _opcode);
            return false;
        }

        stack.push(_payload.value);
        return true;
    }

    @Override
    public byte[] getBytes() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendByte(_opcodeByte);

        if (_payload.valueByteCountLength != null) {
            final Integer byteCount = _payload.value.getByteCount();

            final byte[] payloadByteCountBytes = new byte[_payload.valueByteCountLength];
            ByteUtil.setBytes(payloadByteCountBytes, ByteUtil.reverseEndian(ByteUtil.integerToBytes(byteCount)));
            byteArrayBuilder.appendBytes(payloadByteCountBytes); // NOTE: Payload ByteCount is encoded little-endian, but payloadByteCountBytes is already in this format...
        }

        if (_payload.shouldBeSerialized) {
            byteArrayBuilder.appendBytes(_payload.value.getBytes());
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
        if (! (_payload.value.equals(operation._payload.value))) { return false; }

        return true;
    }

    @Override
    public String toString() {
        return (super.toString() + " Value: " + HexUtil.toHexString(_payload.value.getBytes()));
    }

    @Override
    public String toStandardString() {
        if (_payload.shouldBeSerialized) {
            final String byteCountString = (_payload.valueByteCountLength != null ? (" 0x" + Integer.toHexString(_payload.value.getByteCount()).toUpperCase()) : "");
            final String payloadString = HexUtil.toHexString(_payload.value.getBytes());
            return (super.toStandardString() + byteCountString + " 0x" + payloadString);
        }
        else {
            return super.toStandardString();
        }
    }
}
