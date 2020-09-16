package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableTransactionContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class StringOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_STRING;
    public static final Integer MAX_BYTE_COUNT = 520;

    public static final StringOperation REVERSE_BYTES = new StringOperation((byte) 0xBC, Opcode.REVERSE_BYTES);

    protected static StringOperation fromBytes(final ByteArrayReader byteArrayReader) {
        if (! byteArrayReader.hasBytes()) { return null; }

        final byte opcodeByte = byteArrayReader.readByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final Opcode opcode = TYPE.getSubtype(opcodeByte);
        if (opcode == null) { return null; }

        return new StringOperation(opcodeByte, opcode);
    }

    protected StringOperation(final byte value, final Opcode opcode) {
        super(value, TYPE, opcode);
    }

    @Override
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableTransactionContext context) {
        final UpgradeSchedule upgradeSchedule = context.getUpgradeSchedule();
        final MedianBlockTime medianBlockTime = context.getMedianBlockTime();

        if (! _opcode.isEnabled()) {
            Logger.debug("Opcode is disabled: " + _opcode);
            return false;
        }

        switch (_opcode) {
            case CONCATENATE: {
                // value0 value1 CONCATENATE -> { value0, value1 }
                // { Ox11 } { 0x22, 0x33 } CONCATENATE -> 0x112233

                final Value value1 = stack.pop();
                final Value value0 = stack.pop();

                final int value0ByteCount = value0.getByteCount();
                final int value1ByteCount = value1.getByteCount();
                final int totalByteCount = (value0ByteCount + value1ByteCount);

                // NOTE: The concept of a MAX_BYTE_COUNT is to prevent memory exhaustion attacks, which can happen by repeating
                //  multiple COPY_1ST STRING_CONCATENATE commands multiple times.  For instance, every 10 repetitions,
                //  the memory usage increases 1024 times.
                if (totalByteCount > MAX_BYTE_COUNT) {
                    Logger.debug("Max byte-count exceeded for Opcode: " + _opcode);
                    return false;
                }

                final byte[] concatenatedBytes = new byte[totalByteCount];
                ByteUtil.setBytes(concatenatedBytes, value0.getBytes());
                ByteUtil.setBytes(concatenatedBytes, value1.getBytes(), value0ByteCount);
                final Value newValue = Value.fromBytes(concatenatedBytes);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case SPLIT: {
                // { 0x00, 0x11, 0x22 } 0x00 SPLIT -> { } { 0x00, 0x11, 0x22 }
                // { 0x00, 0x11, 0x22 } 0x01 SPLIT -> { 0x00 } { 0x11, 0x22 }
                // { 0x00, 0x11, 0x22 } 0x02 SPLIT -> { 0x00, 0x11 } { 0x22 }
                // { 0x00, 0x11, 0x22 } 0x03 SPLIT -> { 0x00, 0x11, 0x22 } { }

                final Value beginIndexValue = stack.pop();
                if (! Operation.validateMinimalEncoding(beginIndexValue, context)) { return false; }

                final Value value = stack.pop();

                final byte[] valueBytes = value.getBytes();
                final Integer valueByteCount = valueBytes.length;

                final Integer index = beginIndexValue.asInteger();

                if ( (index < 0) || (index > valueByteCount) ) {
                    Logger.debug("Index out of bounds for Opcode: " + _opcode);
                    return false;
                }

                final int bytes0ByteCount = index;
                final int bytes1ByteCount = (valueByteCount - index);

                final byte[] bytes0 = new byte[bytes0ByteCount];
                final byte[] bytes1 = new byte[bytes1ByteCount];

                ByteUtil.setBytes(bytes0, valueBytes);

                for (int i = 0; i < bytes1ByteCount; ++i) {
                    bytes1[i] = valueBytes[index + i];
                }

                stack.push(Value.fromBytes(bytes0));
                stack.push(Value.fromBytes(bytes1));

                return (! stack.didOverflow());
            }

            // Encodes a signed binary number into Bitcoin's MPI format.
            case ENCODE_NUMBER: {
                // NOTE: Bitcoin Verde's internal representation is always big-endian.
                // value ENCODE_NUMBER -> { minimum-encoded value }
                // { 0x00, 0x00, 0x00, 0x00 } ENCODE_NUMBER -> { }
                // { 0x00, 0x00, 0x00, 0x02 } ENCODE_NUMBER -> { 0x02 }
                // { 0x80, 0x00, 0x05 } ENCODE_NUMBER -> { 0x80, 0x00, 0x05 }

                final Value value = stack.pop();

                final Long valueInteger = value.asLong();
                if (! Operation.isWithinIntegerRange(valueInteger)) { return false; }

                stack.push(Value.fromInteger(valueInteger));

                return (! stack.didOverflow());
            }

            // Decodes an MPI-encoded number into a signed byte array of specific size.
            case NUMBER_TO_BYTES: {
                // value byteCount NUMBER_TO_BYTES -> { value expressed as byteCount bytes }
                // 0x02 0x04 NUMBER_TO_BYTES -> { 0x00, 0x00, 0x00, 0x02 }
                // { 0x85 } { 0x04 } NUMBER_TO_BYTES -> { 0x80, 0x00, 0x00, 0x05 }
                // { 0x85 } { 0x02 } NUMBER_TO_BYTES -> { 0x80, 0x05 }
                // { 0x80, 0xFF } { 0x04 } NUMBER_TO_BYTES -> { 0x80, 0x00, 0x00, 0xFF }

                final Value byteCountValue = stack.pop();
                final Value value = stack.pop();

                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! Operation.isMinimallyEncoded(byteCountValue)) { return false; }
                }

                final int byteCount = byteCountValue.asInteger();

                final MutableByteArray minimallyEncodedByteArray;
                {
                    final Value minimallyEncodedValue = Value.minimallyEncodeBytes(value);
                    if (minimallyEncodedValue == null) { return false; }

                    minimallyEncodedByteArray = new MutableByteArray(minimallyEncodedValue);
                }

                if (byteCount < minimallyEncodedByteArray.getByteCount()) { return false; } // Fail if data is lost during the conversion...

                final boolean isNegative;
                {
                    if (minimallyEncodedByteArray.isEmpty()) {
                        isNegative = false;
                    }
                    else {
                        isNegative = ( (minimallyEncodedByteArray.getByte(minimallyEncodedByteArray.getByteCount() - 1) & 0x80) != 0x00 );
                    }
                }

                { // Remove the sign bit from the minimally encoded value...
                    if (! minimallyEncodedByteArray.isEmpty()) {
                        final int lastIndex = (minimallyEncodedByteArray.getByteCount() - 1);
                        final byte lastByte = minimallyEncodedByteArray.getByte(lastIndex);
                        minimallyEncodedByteArray.setByte(lastIndex, (byte) (lastByte & 0x7F));
                    }
                }

                final MutableByteArray bytes = new MutableByteArray(byteCount);
                ByteUtil.setBytes(bytes, minimallyEncodedByteArray);

                if ( (isNegative) && (byteCount > 0) ) {
                    final int lastByteIndex = (byteCount - 1);
                    final byte lastByte = bytes.getByte(lastByteIndex);
                    bytes.setByte(lastByteIndex, (byte) (lastByte | 0x80));
                }

                final Value decodedValue = Value.fromBytes(bytes);
                stack.push(decodedValue);

                return (! stack.didOverflow());
            }

            case REVERSE_BYTES: {
                if (! upgradeSchedule.isReverseBytesOperationEnabled(medianBlockTime)) { return false; }

                final Value value = stack.pop();
                final Value reversedValue = Value.fromBytes(ByteUtil.reverseEndian(value));
                stack.push(reversedValue);

                return (! stack.didOverflow());
            }

            case PUSH_1ST_BYTE_COUNT: {
                final Value value = stack.peak();
                final long byteCount = value.getByteCount();
                stack.push(Value.fromInteger(byteCount));
                return (! stack.didOverflow());
            }

            default: { return false; }
        }
    }
}
