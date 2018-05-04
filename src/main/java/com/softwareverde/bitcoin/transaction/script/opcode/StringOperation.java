package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.io.Logger;

public class StringOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_STRING;
    public static final Integer MAX_BYTE_COUNT = 520;   // NOTE: This value does not have consensus, since the opcodes that use it are disabled.
                                                        //  However, this value is in parity with the max-bytes per script value.
                                                        //  (https://en.bitcoin.it/wiki/Script#Arithmetic)

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
            case STRING_CONCATENATE: {
                final Value value0 = stack.pop();
                final Value value1 = stack.pop();

                final int value0ByteCount = value0.getByteCount();
                final int value1ByteCount = value1.getByteCount();
                final int totalByteCount = (value0ByteCount + value1ByteCount);

                // NOTE: This opcode is not enabled, and its details have no consensus...
                //  The concept of a MAX_BYTE_COUNT is to prevent memory exhaustion attacks, which can happen by repeating
                //  multiple COPY_1ST STRING_CONCATENATE commands multiple times.  For instance, every 10 repetitions,
                //  the memory usage increases 1024 times.
                if (totalByteCount > MAX_BYTE_COUNT) {
                    Logger.log("NOTICE: Max byte-count exceeded for Opcode: " + _opcode);
                    return false;
                }

                final byte[] concatenatedBytes = new byte[totalByteCount];
                ByteUtil.setBytes(concatenatedBytes, value1.getBytes());
                ByteUtil.setBytes(concatenatedBytes, value0.getBytes(), value0ByteCount);
                final Value newValue = Value.fromBytes(concatenatedBytes);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case STRING_SUBSTRING: {
                final Value value = stack.pop();
                final Value beginIndexValue = stack.pop();
                final Value byteCountValue = stack.pop();

                final Integer beginIndex = beginIndexValue.asInteger();
                final Integer byteCount = byteCountValue.asInteger();

                // NOTE: This opcode is not enabled, and its details have no consensus...
                //  If the operation goes out of bounds, the opcode fails.
                if ((beginIndex < 0) || (byteCount < 0) || (beginIndex + byteCount >= value.getByteCount())) {
                    Logger.log("NOTICE: Index out of bounds for Opcode: " + _opcode);
                    return false;
                }

                final byte[] bytes = new byte[byteCount];
                ByteUtil.setBytes(bytes, value.getBytes(), beginIndex);
                stack.push(Value.fromBytes(bytes));

                return (! stack.didOverflow());
            }

            case STRING_LEFT: {
                final Value value = stack.pop();
                final Value leftByteCountValue = stack.pop();

                final Integer leftByteCount = leftByteCountValue.asInteger();

                // NOTE: This opcode is not enabled, and its details have no consensus...
                //  If the operation goes out of bounds, the opcode fails.
                if ((leftByteCount < 0) || (leftByteCount >= value.getByteCount())) {
                    Logger.log("NOTICE: Index out of bounds for Opcode: " + _opcode);
                    return false;
                }

                final byte[] bytes = new byte[leftByteCount];
                ByteUtil.setBytes(bytes, value.getBytes());
                stack.push(Value.fromBytes(bytes));

                return (! stack.didOverflow());
            }

            case STRING_RIGHT: {
                final Value value = stack.pop();
                final Value skippedByteCountValue = stack.pop();

                final Integer skippedByteCount = skippedByteCountValue.asInteger();

                // NOTE: This opcode is not enabled, and its details have no consensus...
                //  If the operation goes out of bounds, the opcode fails.
                if ((skippedByteCount < 0) || (skippedByteCount >= value.getByteCount())) {
                    Logger.log("NOTICE: Index out of bounds for Opcode: " + _opcode);
                    return false;
                }

                final Integer byteCount = (value.getByteCount() - skippedByteCount);
                final byte[] bytes = ByteUtil.copyBytes(value.getBytes(), skippedByteCount, byteCount);
                stack.push(Value.fromBytes(bytes));

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
