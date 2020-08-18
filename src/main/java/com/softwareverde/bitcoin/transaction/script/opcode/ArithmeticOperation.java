package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableTransactionContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class ArithmeticOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_ARITHMETIC;

    public static final ArithmeticOperation ADD_ONE         = new ArithmeticOperation(Opcode.ADD_ONE.getValue(),            Opcode.ADD_ONE);
    public static final ArithmeticOperation SUBTRACT_ONE    = new ArithmeticOperation(Opcode.SUBTRACT_ONE.getValue(),       Opcode.SUBTRACT_ONE);
    public static final ArithmeticOperation MULTIPLY_BY_TWO = new ArithmeticOperation(Opcode.MULTIPLY_BY_TWO.getValue(),    Opcode.MULTIPLY_BY_TWO);
    public static final ArithmeticOperation DIVIDE_BY_TWO   = new ArithmeticOperation(Opcode.DIVIDE_BY_TWO.getValue(),      Opcode.DIVIDE_BY_TWO);
    public static final ArithmeticOperation NEGATE          = new ArithmeticOperation(Opcode.NEGATE.getValue(),             Opcode.NEGATE);
    public static final ArithmeticOperation ABSOLUTE_VALUE  = new ArithmeticOperation(Opcode.ABSOLUTE_VALUE.getValue(),     Opcode.ABSOLUTE_VALUE);
    public static final ArithmeticOperation NOT             = new ArithmeticOperation(Opcode.NOT.getValue(),                Opcode.NOT);
    public static final ArithmeticOperation ADD             = new ArithmeticOperation(Opcode.ADD.getValue(),                Opcode.ADD);
    public static final ArithmeticOperation SUBTRACT        = new ArithmeticOperation(Opcode.SUBTRACT.getValue(),           Opcode.SUBTRACT);
    public static final ArithmeticOperation MULTIPLY        = new ArithmeticOperation(Opcode.MULTIPLY.getValue(),           Opcode.MULTIPLY);
    public static final ArithmeticOperation DIVIDE          = new ArithmeticOperation(Opcode.DIVIDE.getValue(),             Opcode.DIVIDE);
    public static final ArithmeticOperation MODULUS         = new ArithmeticOperation(Opcode.MODULUS.getValue(),            Opcode.MODULUS);
    public static final ArithmeticOperation MIN             = new ArithmeticOperation(Opcode.MIN.getValue(),                Opcode.MIN);
    public static final ArithmeticOperation MAX             = new ArithmeticOperation(Opcode.MAX.getValue(),                Opcode.MAX);

    protected static ArithmeticOperation fromBytes(final ByteArrayReader byteArrayReader) {
        if (! byteArrayReader.hasBytes()) { return null; }

        final byte opcodeByte = byteArrayReader.readByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final Opcode opcode = TYPE.getSubtype(opcodeByte);
        if (opcode == null) { return null; }

        return new ArithmeticOperation(opcodeByte, opcode);
    }

    protected ArithmeticOperation(final byte value, final Opcode opcode) {
        super(value, TYPE, opcode);
    }

    @Override
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableTransactionContext context) {
        if (! _opcode.isEnabled()) {
            Logger.debug("Opcode is disabled: " + _opcode);
            return false;
        }

        switch (_opcode) {
            case ADD_ONE: {
                final Value value = stack.pop();
                if (! Operation.validateMinimalEncoding(value, context)) { return false; }

                final Long longValue = value.asLong();
                if (! Operation.isWithinIntegerRange(longValue)) { return false; }

                final Long newIntValue = (longValue + 1L);

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case SUBTRACT_ONE: {
                final Value value = stack.pop();
                if (! Operation.validateMinimalEncoding(value, context)) { return false; }

                final Long longValue = value.asLong();
                if (! Operation.isWithinIntegerRange(longValue)) { return false; }

                final Long newIntValue = (longValue - 1L);

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case MULTIPLY_BY_TWO: {
                final Value value = stack.pop();
                final Long longValue = value.asLong();
                if (! Operation.isWithinIntegerRange(longValue)) { return false; }

                final Long newIntValue = (longValue * 2L);

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case DIVIDE_BY_TWO: {
                final Value value = stack.pop();
                final Long longValue = value.asLong();
                if (! Operation.isWithinIntegerRange(longValue)) { return false; }

                final Long newIntValue = (longValue / 2L);

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case NEGATE: {
                final Value value = stack.pop();
                if (! Operation.validateMinimalEncoding(value, context)) { return false; }

                final Long longValue = value.asLong();
                if (! Operation.isWithinIntegerRange(longValue)) { return false; }

                final Long newIntValue = (-longValue);

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case ABSOLUTE_VALUE: {
                final Value value = stack.pop();
                if (! Operation.validateMinimalEncoding(value, context)) { return false; }

                final Long longValue = value.asLong();
                if (! Operation.isWithinIntegerRange(longValue)) { return false; }

                final Long newIntValue = Math.abs(longValue);

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case NOT: {
                final Value value = stack.pop();
                if (! Operation.validateMinimalEncoding(value, context)) { return false; }

                final Long longValue = value.asLong();
                if (! Operation.isWithinIntegerRange(longValue)) { return false; }

                final Long newLongValue = (longValue == 0L ? 1L : 0L);

                final Value newValue = Value.fromInteger(newLongValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case ADD: {
                final Value value1 = stack.pop();
                if (! Operation.validateMinimalEncoding(value1, context)) { return false; }

                final Value value0 = stack.pop();
                if (! Operation.validateMinimalEncoding(value0, context)) { return false; }

                final Long longValue0 = value0.asLong();
                if (! Operation.isWithinIntegerRange(longValue0)) { return false; }

                final Long longValue1 = value1.asLong();
                if (! Operation.isWithinIntegerRange(longValue1)) { return false; }

                final Long newIntValue = (longValue0 + longValue1);

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case SUBTRACT: {
                final Value value1 = stack.pop();
                if (! Operation.validateMinimalEncoding(value1, context)) { return false; }

                final Value value0 = stack.pop();
                if (! Operation.validateMinimalEncoding(value0, context)) { return false; }

                final Long longValue0 = value0.asLong();
                if (! Operation.isWithinIntegerRange(longValue0)) { return false; }

                final Long longValue1 = value1.asLong();
                if (! Operation.isWithinIntegerRange(longValue1)) { return false; }

                final Long newIntValue = (longValue0 - longValue1);

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case MULTIPLY: {
                final Value value1 = stack.pop();
                if (! Operation.validateMinimalEncoding(value1, context)) { return false; }

                final Value value0 = stack.pop();
                if (! Operation.validateMinimalEncoding(value0, context)) { return false; }

                final Long longValue0 = value0.asLong();
                if (! Operation.isWithinIntegerRange(longValue0)) { return false; }

                final Long longValue1 = value1.asLong();
                if (! Operation.isWithinIntegerRange(longValue1)) { return false; }

                final Long newIntValue = (longValue1 * longValue0);

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case DIVIDE: {
                // value0 value1 DIVIDE -> { value0 / value1 }
                // { 0x0A } { 0x02 } DIVIDE -> { 0x05 }

                final Value value1 = stack.pop(); // Divisor
                if (! Operation.validateMinimalEncoding(value1, context)) { return false; }

                final Value value0 = stack.pop();
                if (! Operation.validateMinimalEncoding(value0, context)) { return false; }

                final Long longValue0 = value0.asLong();
                if (! Operation.isWithinIntegerRange(longValue0)) { return false; }

                final Long longValue1 = value1.asLong();
                if (! Operation.isWithinIntegerRange(longValue1)) { return false; }

                if (longValue1 == 0) { return false; }

                final Long newIntValue = (longValue0 / longValue1);

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case MODULUS: {
                // value0 value1 MODULUS -> { value0 % value1 }
                // { 0x0A } { 0x02 } MODULUS -> { 0x00 }

                final Value value1 = stack.pop();
                if (! Operation.validateMinimalEncoding(value1, context)) { return false; }

                final Value value0 = stack.pop();
                if (! Operation.validateMinimalEncoding(value0, context)) { return false; }

                final Long longValue0 = value0.asLong();
                if (! Operation.isWithinIntegerRange(longValue0)) { return false; }

                final Long longValue1 = value1.asLong();
                if (! Operation.isWithinIntegerRange(longValue1)) { return false; }

                if (longValue1 == 0) { return false; }

                final Long newIntValue = (longValue0 % longValue1);

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case MIN: {
                final Value value1 = stack.pop();
                if (! Operation.validateMinimalEncoding(value1, context)) { return false; }

                final Value value0 = stack.pop();
                if (! Operation.validateMinimalEncoding(value0, context)) { return false; }

                final Long longValue0 = value0.asLong();
                if (! Operation.isWithinIntegerRange(longValue0)) { return false; }

                final Long longValue1 = value1.asLong();
                if (! Operation.isWithinIntegerRange(longValue1)) { return false; }

                final Long newIntValue = Math.min(longValue1, longValue0);

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case MAX: {
                final Value value1 = stack.pop();
                if (! Operation.validateMinimalEncoding(value1, context)) { return false; }

                final Value value0 = stack.pop();
                if (! Operation.validateMinimalEncoding(value0, context)) { return false; }

                final Long longValue0 = value0.asLong();
                if (! Operation.isWithinIntegerRange(longValue0)) { return false; }

                final Long longValue1 = value1.asLong();
                if (! Operation.isWithinIntegerRange(longValue1)) { return false; }

                final Long newIntValue = Math.max(longValue1, longValue0);

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            default: { return false; }
        }
    }
}
