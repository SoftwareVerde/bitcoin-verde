package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableTransactionContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class BitwiseOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_BITWISE;

    public static BitwiseOperation BITWISE_INVERT = new BitwiseOperation(Opcode.BITWISE_INVERT);
    public static BitwiseOperation BITWISE_AND = new BitwiseOperation(Opcode.BITWISE_AND);
    public static BitwiseOperation BITWISE_OR = new BitwiseOperation(Opcode.BITWISE_OR);
    public static BitwiseOperation BITWISE_XOR = new BitwiseOperation(Opcode.BITWISE_XOR);
    public static BitwiseOperation SHIFT_LEFT = new BitwiseOperation(Opcode.SHIFT_LEFT);
    public static BitwiseOperation SHIFT_RIGHT = new BitwiseOperation(Opcode.SHIFT_RIGHT);

    protected static BitwiseOperation fromBytes(final ByteArrayReader byteArrayReader) {
        if (! byteArrayReader.hasBytes()) { return null; }

        final byte opcodeByte = byteArrayReader.readByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final Opcode opcode = TYPE.getSubtype(opcodeByte);
        if (opcode == null) { return null; }

        return new BitwiseOperation(opcodeByte, opcode);
    }

    protected BitwiseOperation(final Opcode opcode) {
        super(opcode.getValue(), TYPE, opcode);
    }

    protected BitwiseOperation(final byte value, final Opcode opcode) {
        super(value, TYPE, opcode);
    }

    @Override
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableTransactionContext context) {
        if (! _opcode.isEnabled()) {
            Logger.debug("NOTICE: Opcode is disabled: " + _opcode);
            return false;
        }

        switch (_opcode) {
            case BITWISE_INVERT: {
                final Value value = stack.pop();
                final MutableByteArray byteValue = MutableByteArray.wrap(value.getBytes());
                final int byteCount = byteValue.getByteCount();

                for (int i = 0; i < byteCount; ++i) {
                    final byte b = byteValue.getByte(i);
                    final byte newB = (byte) ~b;
                    byteValue.setByte(i, newB);
                }
                stack.push(Value.fromBytes(byteValue.unwrap()));

                return (! stack.didOverflow());
            }

            case BITWISE_AND: {
                // value0 value1 BITWISE_AND -> { value0 & value1 }
                // { 0x00 } { 0x01 } BITWISE_AND -> { 0x00 }

                final Value value1 = stack.pop();
                final Value value0 = stack.pop();

                final MutableByteArray byteValue0 = MutableByteArray.wrap(value0.getBytes());
                final ByteArray byteValue1 = MutableByteArray.wrap(value1.getBytes());

                if (byteValue0.getByteCount() != byteValue1.getByteCount()) { return false; }

                final int byteCount = byteValue0.getByteCount();

                for (int i = 0; i < byteCount; ++i) {
                    final byte b0 = byteValue0.getByte(i);
                    final byte b1 = byteValue1.getByte(i);
                    final byte newB = (byte) (b0 & b1);
                    byteValue0.setByte(i, newB);
                }
                stack.push(Value.fromBytes(byteValue0.unwrap()));

                return (! stack.didOverflow());
            }

            case BITWISE_OR: {
                // value0 value1 BITWISE_OR -> { value0 | value1 }
                // { 0x00 } { 0x01 } BITWISE_OR -> { 0x01 }

                final Value value1 = stack.pop();
                final Value value0 = stack.pop();

                final MutableByteArray byteValue0 = MutableByteArray.wrap(value0.getBytes());
                final ByteArray byteValue1 = MutableByteArray.wrap(value1.getBytes());

                if (byteValue0.getByteCount() != byteValue1.getByteCount()) { return false; }

                final int byteCount = byteValue0.getByteCount();

                for (int i = 0; i < byteCount; ++i) {
                    final byte b0 = byteValue0.getByte(i);
                    final byte b1 = byteValue1.getByte(i);
                    final byte newB = (byte) (b0 | b1);
                    byteValue0.setByte(i, newB);
                }
                stack.push(Value.fromBytes(byteValue0.unwrap()));

                return (! stack.didOverflow());
            }

            case BITWISE_XOR: {
                // value0 value1 BITWISE_XOR -> { value0 ^ value1 }
                // { 0x01 } { 0x01 } BITWISE_XOR -> { 0x00 }

                final Value value1 = stack.pop();
                final Value value0 = stack.pop();

                final MutableByteArray byteValue0 = MutableByteArray.wrap(value0.getBytes());
                final ByteArray byteValue1 = MutableByteArray.wrap(value1.getBytes());

                if (byteValue0.getByteCount() != byteValue1.getByteCount()) { return false; }

                final int byteCount = byteValue0.getByteCount();

                for (int i = 0; i < byteCount; ++i) {
                    final byte b0 = byteValue0.getByte(i);
                    final byte b1 = byteValue1.getByte(i);
                    final byte newB = (byte) (b0 ^ b1);
                    byteValue0.setByte(i, newB);
                }
                stack.push(Value.fromBytes(byteValue0.unwrap()));

                return (! stack.didOverflow());
            }

            case SHIFT_LEFT: {
                final Value bitShiftCountValue = stack.pop();
                final MutableByteArray value = new MutableByteArray(stack.pop());

                if (! bitShiftCountValue.isMinimallyEncodedInteger()) { return false; }
                final Integer bitShiftCount = bitShiftCountValue.asInteger();
                if (bitShiftCount < 0) { return false; }

                final int byteCount = value.getByteCount();
                final int bitCount = (byteCount * 8);

                if (bitShiftCount < bitCount) {
                    for (int i = 0; i < bitCount; ++i) {
                        final int writeIndex = i;
                        final int readIndex = (writeIndex + bitShiftCount);
                        final boolean newValue = ( (readIndex < bitCount) && value.getBit(readIndex) );
                        value.setBit(writeIndex, newValue);
                    }
                    stack.push(Value.fromBytes(value.unwrap()));
                }
                else {
                    stack.push(Value.fromBytes(new byte[byteCount]));
                }

                return (! stack.didOverflow());
            }

            case SHIFT_RIGHT: {
                final Value bitShiftCountValue = stack.pop();
                final MutableByteArray value = new MutableByteArray(stack.pop());

                if (! bitShiftCountValue.isMinimallyEncodedInteger()) { return false; }
                final Integer bitShiftCount = bitShiftCountValue.asInteger();
                if (bitShiftCount < 0) { return false; }

                final int byteCount = value.getByteCount();
                final int bitCount = (byteCount * 8);

                if (bitShiftCount < bitCount) {
                    for (int i = 0; i < bitCount; ++i) {
                        final int writeIndex = (bitCount - i - 1);
                        final int readIndex = (writeIndex - bitShiftCount);
                        final boolean newValue = ( (readIndex >= 0) && value.getBit(readIndex) );
                        value.setBit(writeIndex, newValue);
                    }
                    stack.push(Value.fromBytes(value.unwrap()));
                }
                else {
                    stack.push(Value.fromBytes(new byte[byteCount]));
                }

                return (! stack.didOverflow());
            }

            default: { return false; }
        }
    }
}
