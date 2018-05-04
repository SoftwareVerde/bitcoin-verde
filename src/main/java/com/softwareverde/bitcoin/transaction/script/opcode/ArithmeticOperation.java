package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.io.Logger;

public class ArithmeticOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_ARITHMETIC;

    protected static ArithmeticOperation fromBytes(final ByteArrayReader byteArrayReader) {
        if (! byteArrayReader.hasBytes()) { return null; }

        final byte opcodeByte = byteArrayReader.readByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final Opcode opcode = TYPE.getSubtype(opcodeByte);
        if (opcode == null) { return null; }

        return new ArithmeticOperation(opcodeByte, opcode);
    }

    protected static Boolean integerOverflowed(final long value) {
        return (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE);
    }

    protected ArithmeticOperation(final byte value, final Opcode opcode) {
        super(value, TYPE, opcode);
    }

    @Override
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableContext context) {
        context.incrementCurrentLockingScriptIndex();

        // Meh.
        if (! _opcode.isEnabled()) {
            Logger.log("NOTICE: Opcode is disabled: " + _opcode);
            return false;
        }

        switch (_opcode) {
            case ADD_ONE: {
                final Value value = stack.pop();
                final Long intValue = value.asInteger().longValue();

                final Long newIntValue = (intValue + 1L);
                if (integerOverflowed(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue.intValue());
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case SUBTRACT_ONE: {
                final Value value = stack.pop();
                final Long intValue = value.asInteger().longValue();

                final Long newIntValue = (intValue - 1L);
                if (integerOverflowed(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue.intValue());
                stack.push(newValue);

                return (! stack.didOverflow());
            }
            case MULTIPLY_BY_TWO: {
                final Value value = stack.pop();
                final Long intValue = value.asInteger().longValue();

                final Long newIntValue = (intValue * 2L);
                if (integerOverflowed(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue.intValue());
                stack.push(newValue);

                return (! stack.didOverflow());
            }
            case DIVIDE_BY_TWO: {
                final Value value = stack.pop();
                final Long intValue = value.asInteger().longValue();

                final Long newIntValue = (intValue / 2L);
                if (integerOverflowed(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue.intValue());
                stack.push(newValue);

                return (! stack.didOverflow());
            }
            case NEGATE: {
                final Value value = stack.pop();
                final Long intValue = value.asInteger().longValue();

                final Long newIntValue = (-intValue);
                if (integerOverflowed(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue.intValue());
                stack.push(newValue);

                return (! stack.didOverflow());
            }
            case ABSOLUTE_VALUE: {
                final Value value = stack.pop();
                final Long intValue = value.asInteger().longValue();

                final Long newIntValue = Math.abs(intValue);
                if (integerOverflowed(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue.intValue());
                stack.push(newValue);

                return (! stack.didOverflow());
            }
            case NOT: {
                final Value value = stack.pop();
                final Integer intValue = value.asInteger();

                final Integer newIntValue = (intValue == 0 ? 1 : 0);

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }
            case ADD: {
                final Value value0 = stack.pop();
                final Long intValue0 = value0.asInteger().longValue();

                final Value value1 = stack.pop();
                final Long intValue1 = value1.asInteger().longValue();

                final Long newIntValue = (intValue0 + intValue1);
                if (integerOverflowed(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue.intValue());
                stack.push(newValue);

                return (! stack.didOverflow());
            }
            case SUBTRACT: {
                final Value value0 = stack.pop();
                final Long intValue0 = value0.asInteger().longValue();

                final Value value1 = stack.pop();
                final Long intValue1 = value1.asInteger().longValue();

                final Long newIntValue = (intValue0 - intValue1);
                if (integerOverflowed(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue.intValue());
                stack.push(newValue);

                return (! stack.didOverflow());
            }
            case MULTIPLY: {
                final Value value0 = stack.pop();
                final Long intValue0 = value0.asInteger().longValue();

                final Value value1 = stack.pop();
                final Long intValue1 = value1.asInteger().longValue();

                final Long newIntValue = (intValue0 * intValue1);
                if (integerOverflowed(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue.intValue());
                stack.push(newValue);

                return (! stack.didOverflow());
            }
            case DIVIDE: {
                final Value value0 = stack.pop();
                final Long intValue0 = value0.asInteger().longValue();

                final Value value1 = stack.pop();
                final Long intValue1 = value1.asInteger().longValue();

                final Long newIntValue = (intValue0 / intValue1);
                if (integerOverflowed(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue.intValue());
                stack.push(newValue);

                return (! stack.didOverflow());
            }
            case MODULUS: {
                final Value value0 = stack.pop();
                final Long intValue0 = value0.asInteger().longValue();

                final Value value1 = stack.pop();
                final Long intValue1 = value1.asInteger().longValue();

                final Long newIntValue = (intValue0 % intValue1);
                if (integerOverflowed(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue.intValue());
                stack.push(newValue);

                return (! stack.didOverflow());
            }
            case MIN: {
                final Value value0 = stack.pop();
                final Long intValue0 = value0.asInteger().longValue();

                final Value value1 = stack.pop();
                final Long intValue1 = value1.asInteger().longValue();

                final Long newIntValue = Math.min(intValue0, intValue1);
                if (integerOverflowed(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue.intValue());
                stack.push(newValue);

                return (! stack.didOverflow());
            }
            case MAX: {
                final Value value0 = stack.pop();
                final Long intValue0 = value0.asInteger().longValue();

                final Value value1 = stack.pop();
                final Long intValue1 = value1.asInteger().longValue();

                final Long newIntValue = Math.max(intValue0, intValue1);
                if (integerOverflowed(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue.intValue());
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            default: { return false; }
        }
    }
}
