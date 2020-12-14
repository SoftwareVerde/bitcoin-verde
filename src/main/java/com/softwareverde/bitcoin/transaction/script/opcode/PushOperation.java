package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableTransactionContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class PushOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_PUSH;
    public static final Integer VALUE_MAX_BYTE_COUNT = 520; // NOTE: Values should not be larger than 520 bytes in size. https://github.com/bitcoin/bitcoin/blob/v0.10.0rc3/src/script/script.h#L18

    public static final PushOperation PUSH_ZERO = new PushOperation(Opcode.PUSH_ZERO.getValue(), Opcode.PUSH_ZERO, new Payload(false, null, Value.fromInteger(0L)));

    protected static class Payload {
        public final Boolean shouldBeSerialized;
        /**
         *  The raw bytes used to indicate the length of the value to be pushed.
         *  This value is expected to be in little-endian format, which is its original serialized form on the stack.
         */
        public final ByteArray valueLengthBytes;
        public final Value value;

        public Payload(final Boolean shouldBeSerialized, final ByteArray valueLengthBytes, final Value value) {
            this.shouldBeSerialized = shouldBeSerialized;
            this.valueLengthBytes = valueLengthBytes;
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
                final Value value = Value.fromInteger(-1L);
                payload = new Payload(false, null, value);
            } break;

            // Pushes the literal value 0 to the stack.
            case PUSH_ZERO: {
                final Value value = Value.fromInteger(0L);
                payload = new Payload(false, null, value);
            } break;

            // Interprets the opcode's value as an integer offset.  Then its value (+1) is pushed to the stack.
            //  (i.e. the literals 1-16)
            case PUSH_VALUE: {
                final int offset = (ByteUtil.byteToInteger(opcodeByte) - Opcode.PUSH_VALUE.getMinValue());
                final long pushedValue = (offset + 1);
                final Value value = Value.fromInteger(pushedValue);
                payload = new Payload(false, null, value);
            } break;

            // Interprets the opcode's value as an integer ("N").  Then, the next N bytes are pushed to the stack.
            case PUSH_DATA: {
                final int byteCount = ByteUtil.byteToInteger(opcodeByte);
                { // Validate byteCount...
                    if (byteCount < 0) { return null; }
                    if (byteCount > byteArrayReader.remainingByteCount()) { return null; }
                }

                final Value value = Value.fromBytes(byteArrayReader.readBytes(byteCount));
                if (value == null) { return null; }

                payload = new Payload(true, null, value);
            } break;

            // Pushes the next byte as an integer ("N").  Then, the next N bytes are pushed to the stack.
            case PUSH_DATA_BYTE: {
                final Integer valueByteCountLength = 1;
                final ByteArray lengthBytes = ByteArray.wrap(byteArrayReader.readBytes(valueByteCountLength));
                final int byteCount = ByteUtil.bytesToInteger(lengthBytes.toReverseEndian());
                { // Validate byteCount...
                    if (byteCount < 0) { return null; }
                    if (byteCount > byteArrayReader.remainingByteCount()) { return null; }
                }

                final Value value = Value.fromBytes(byteArrayReader.readBytes(byteCount));
                if (value == null) { return null; }

                payload = new Payload(true, lengthBytes, value);
            } break;

            // Interprets the next 2 bytes as an integer ("N").  Then, the next N bytes are pushed to the stack.
            case PUSH_DATA_SHORT: {
                final Integer valueByteCountLength = 2;
                final ByteArray lengthBytes = ByteArray.wrap(byteArrayReader.readBytes(valueByteCountLength));
                final int byteCount = ByteUtil.bytesToInteger(lengthBytes.toReverseEndian());
                { // Validate byteCount...
                    if (byteCount < 0) { return null; }
                    if (byteCount > byteArrayReader.remainingByteCount()) { return null; }
                }

                final Value value = Value.fromBytes(byteArrayReader.readBytes(byteCount));
                if (value == null) { return null; }

                payload = new Payload(true, lengthBytes, value);
            } break;

            // Interprets the next 4 bytes as an integer ("N").  Then, the next N bytes are pushed to the stack.
            case PUSH_DATA_INTEGER: {
                final Integer valueByteCountLength = 4;
                final ByteArray lengthBytes = ByteArray.wrap(byteArrayReader.readBytes(valueByteCountLength));
                final int byteCount = ByteUtil.bytesToInteger(lengthBytes.toReverseEndian());
                { // Validate byteCount...
                    if (byteCount < 0) { return null; }
                    if (byteCount > byteArrayReader.remainingByteCount()) { return null; }
                }

                final Value value = Value.fromBytes(byteArrayReader.readBytes(byteCount));
                if (value == null) { return null; }

                payload = new Payload(true, lengthBytes, value);
            } break;

            case PUSH_VERSION: {
                final Value value = Value.fromBytes(StringUtil.stringToBytes(BitcoinConstants.getUserAgent()));
                payload = new Payload(false, null, value);
            } break;

            default: { return null; }
        }

        if (byteArrayReader.didOverflow()) { return null; }

        return new PushOperation(opcodeByte, opcode, payload);
    }

    /**
     * Creates an appropriately sized PushOperation for the provided bytes.
     *  If `byteArray` is empty, a non-minimally-encoded operation will be returned.
     *  Consider using PushOperation.ZERO instead to intentionally push an empty ByteArray.
     */
    public static PushOperation pushBytes(final ByteArray byteArray) {
        if (byteArray == null) { return null; }

        final int byteCount = byteArray.getByteCount();
        if (byteCount > VALUE_MAX_BYTE_COUNT) { return null; }

        if (byteCount == 0) {
            final ByteArray lengthBytes = new MutableByteArray(1);
            final Payload payload = new Payload(true, lengthBytes, Value.ZERO);
            return new PushOperation(Opcode.PUSH_DATA_BYTE.getValue(), Opcode.PUSH_DATA_BYTE, payload);
        }
        else if (byteCount <= Opcode.PUSH_DATA.getMaxValue()) {
            final Value value = Value.fromBytes(byteArray);
            final Payload payload = new Payload(true, null, value);
            return new PushOperation((byte) byteCount, Opcode.PUSH_DATA, payload);
        }
        else if (byteCount < (1 << 8)) {
            final ByteArray lengthBytes = ByteArray.wrap(ByteUtil.getTailBytes(ByteUtil.integerToBytes(byteCount), 1)).toReverseEndian();
            final Value value = Value.fromBytes(byteArray);
            final Payload payload = new Payload(true, lengthBytes, value);
            return new PushOperation(Opcode.PUSH_DATA_BYTE.getValue(), Opcode.PUSH_DATA_BYTE, payload);
        }
        else if (byteCount < (1 << 16)) {
            final ByteArray lengthBytes = ByteArray.wrap(ByteUtil.getTailBytes(ByteUtil.integerToBytes(byteCount), 2)).toReverseEndian();
            final Value value = Value.fromBytes(byteArray);
            final Payload payload = new Payload(true, lengthBytes, value);
            return new PushOperation(Opcode.PUSH_DATA_SHORT.getValue(), Opcode.PUSH_DATA_SHORT, payload);
        }
        else {
            final ByteArray lengthBytes = ByteArray.wrap(ByteUtil.getTailBytes(ByteUtil.integerToBytes(byteCount), 4)).toReverseEndian();
            final Value value = Value.fromBytes(byteArray);
            final Payload payload = new Payload(true, lengthBytes, value);
            return new PushOperation(Opcode.PUSH_DATA_INTEGER.getValue(), Opcode.PUSH_DATA_INTEGER, payload);
        }
    }

    /**
     * Creates a PushOperation with the provided bytes.
     *  Uses the network's current minimal data encoding rules to ensure that values that require special op-codes
     *  (e.g. OP_0 for "0x00") are returned with the appropriate push operation.
     * @param value
     * @return
     */
    public static PushOperation pushValue(final Value value) {
        if (value.getByteCount() == 0) {
            return PUSH_ZERO;
        }
        if (value.getByteCount() == 1) {
            final byte valueByte = value.getByte(0);
            if (valueByte == (byte) 0x81) {
                return new PushOperation(Opcode.PUSH_NEGATIVE_ONE.getValue(), Opcode.PUSH_NEGATIVE_ONE, new Payload(false, null, value));
            }
            else if (valueByte >= 0x01 && valueByte <= 0x10) {
                final byte opcode = (byte) ((valueByte - 1) + Opcode.PUSH_VALUE.getMinValue());
                return new PushOperation(opcode, Opcode.PUSH_VALUE, new Payload(false, null, value));
            }
        }
        return PushOperation.pushBytes(value);
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
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableTransactionContext context) {
        if (! _opcode.isEnabled()) {
            Logger.debug("Opcode is disabled: " + _opcode);
            return false;
        }

        final UpgradeSchedule upgradeSchedule = context.getUpgradeSchedule();
        final MedianBlockTime medianBlockTime = context.getMedianBlockTime();
        if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
            if (_payload.valueLengthBytes != null) {
                // pushes of various lengths must be done with the required op-code
                final int lengthValue = ByteUtil.bytesToInteger(ByteUtil.reverseEndian(_payload.valueLengthBytes));
                if (lengthValue < 76) {
                    Logger.debug("Push of length " + lengthValue + " must use specialized op-code.");
                    return false;
                }
                else if (lengthValue <= 255) {
                    if (_opcode != Opcode.PUSH_DATA_BYTE) {
                        Logger.debug("Push of length " + lengthValue + " must use op-code " + Opcode.PUSH_DATA_BYTE);
                        return false;
                    }
                }
                else if (lengthValue <= 65535) {
                    if (_opcode != Opcode.PUSH_DATA_SHORT) {
                        Logger.debug("Push of length " + lengthValue + " must use op-code " + Opcode.PUSH_DATA_BYTE);
                        return false;
                    }
                }
            }
            else {
                // ensure empty pushes are only done with OP_0
                if (_payload.value.getByteCount() == 0) {
                    if (_opcode != Opcode.PUSH_ZERO) {
                        Logger.debug("Push of length 0 must be done with op-code " + Opcode.PUSH_ZERO);
                        return false;
                    }
                }

                // if pushing a single byte, with OpCode.PUSH_DATA (i.e. op-code 0x01),
                // ensure it is not a case where more specific op-code should have been used.
                if (_opcode == Opcode.PUSH_DATA && _payload.value.getByteCount() == 1) {
                    byte value = _payload.value.getByte(0);
                    if ( (value == (byte) 0x81) || (value >= 0x01 && value <= 0x10) ) {
                        Logger.debug("Single-byte push operation used where a more specialized operation is required.");
                        return false;
                    }
                }
            }
        }

        stack.push(_payload.value);
        return true;
    }

    @Override
    public byte[] getBytes() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendByte(_opcodeByte);

        if (_payload.valueLengthBytes != null) {
            final byte[] payloadByteCountBytes = _payload.valueLengthBytes.getBytes();
            byteArrayBuilder.appendBytes(payloadByteCountBytes); // NOTE: Payload ByteCount is encoded little-endian, but valueLengthBytes is already in this format...
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
            final String byteCountString = (_payload.valueLengthBytes != null ? (" 0x" + Integer.toHexString(_payload.value.getByteCount()).toUpperCase()) : "");
            final String payloadString = HexUtil.toHexString(_payload.value.getBytes());
            return (super.toStandardString() + byteCountString + " 0x" + payloadString);
        }
        else {
            return super.toStandardString();
        }
    }
}
