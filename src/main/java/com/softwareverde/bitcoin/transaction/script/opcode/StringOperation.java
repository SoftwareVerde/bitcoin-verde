package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class StringOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_STRING;
    public static final Integer MAX_BYTE_COUNT = 520;

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
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableContext context) {

        // Meh.
        if (! _opcode.isEnabled()) {
            Logger.log("NOTICE: Opcode is disabled: " + _opcode);
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
                    Logger.log("NOTICE: Max byte-count exceeded for Opcode: " + _opcode);
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
                final Value value = stack.pop();

                final byte[] valueBytes = value.getBytes();
                final Integer valueByteCount = valueBytes.length;

                final Integer index = beginIndexValue.asInteger();

                if ( (index < 0) || (index > valueByteCount) ) {
                    Logger.log("NOTICE: Index out of bounds for Opcode: " + _opcode);
                    return false;
                }

                final Integer bytes0ByteCount = index;
                final Integer bytes1ByteCount = (valueByteCount - index);

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
            case ENCODE_NUMBER: { // TODO: Write tests for this implementation...
                // NOTE: Bitcoin Verde's internal representation is always big-endian.
                // value ENCODE_NUMBER -> { minimum-encoded value }
                // { 0x00, 0x00, 0x00, 0x00 } ENCODE_NUMBER -> { }
                // { 0x00, 0x00, 0x00, 0x02 } ENCODE_NUMBER -> { 0x02 }
                // { 0x80, 0x00, 0x05 } ENCODE_NUMBER -> { 0x80, 0x00, 0x05 }

                final Value value = stack.pop();

                final Long valueInteger = value.asLong();
                if (_didIntegerOverflow(valueInteger)) { return false; }

                stack.push(Value.fromInteger(valueInteger));

                return (! stack.didOverflow());
            }

            // Decodes an MPI-encoded number into a signed byte array of specific size.
            case DECODE_NUMBER: { // TODO: Write tests for this implementation...
                // value byteCount DECODE_NUMBER -> { value expressed as byteCount bytes }
                // 0x02 0x04 DECODE_NUMBER -> { 0x00, 0x00, 0x00, 0x02 }
                // { 0x85 } { 0x04 } DECODE_NUMBER -> { 0x80, 0x00, 0x00, 0x05 }
                // { 0x85 } { 0x02 } DECODE_NUMBER -> { 0x80, 0x05 }
                // { 0x80, 0xFF } { 0x04 } DECODE_NUMBER -> { 0x80, 0x00, 0x00, 0xFF }

                final Value byteCountValue = stack.pop();
                final Value value = stack.pop();

                final Integer byteCount = byteCountValue.asInteger();
                final Integer valueAsInteger = value.asInteger();

                final byte[] minimallyEncodedValue = Value.fromInteger(valueAsInteger.longValue()).getBytes();
                if (byteCount < minimallyEncodedValue.length) { return false; }

                final Boolean isNegative = ( valueAsInteger < 0 );

                { // Remove the sign bit from the minimally encoded value...
                    if (minimallyEncodedValue.length > 0) {
                        minimallyEncodedValue[0] &= 0x7F;
                    }
                }

                final byte[] bytes = new byte[byteCount];
                for (int i = 0; i < minimallyEncodedValue.length; ++i) {
                    final byte b = minimallyEncodedValue[minimallyEncodedValue.length - i - 1];
                    bytes[bytes.length - i - 1] = b;
                }

                if (isNegative) {
                    bytes[0] |= (byte) 0x80;
                }

                final byte[] littleEndianBytes = ByteUtil.reverseEndian(bytes);
                final Value decodedValue = Value.fromBytes(littleEndianBytes);
                stack.push(decodedValue);

                return (! stack.didOverflow());
            }

            case STRING_PUSH_LENGTH: {
                final Value value = stack.peak();
                final long byteCount = value.getByteCount();
                stack.push(Value.fromInteger(byteCount));
                return (! stack.didOverflow());
            }

            default: { return false; }
        }
    }
}
