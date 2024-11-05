package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableTransactionContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.util.MathUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class ArithmeticOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_ARITHMETIC;

    public static final ArithmeticOperation ADD_ONE         = new ArithmeticOperation(Opcode.ADD_ONE);
    public static final ArithmeticOperation SUBTRACT_ONE    = new ArithmeticOperation(Opcode.SUBTRACT_ONE);
    public static final ArithmeticOperation MULTIPLY_BY_TWO = new ArithmeticOperation(Opcode.MULTIPLY_BY_TWO);
    public static final ArithmeticOperation DIVIDE_BY_TWO   = new ArithmeticOperation(Opcode.DIVIDE_BY_TWO);
    public static final ArithmeticOperation NEGATE          = new ArithmeticOperation(Opcode.NEGATE);
    public static final ArithmeticOperation ABSOLUTE_VALUE  = new ArithmeticOperation(Opcode.ABSOLUTE_VALUE);
    public static final ArithmeticOperation NOT             = new ArithmeticOperation(Opcode.NOT);
    public static final ArithmeticOperation ADD             = new ArithmeticOperation(Opcode.ADD);
    public static final ArithmeticOperation SUBTRACT        = new ArithmeticOperation(Opcode.SUBTRACT);
    public static final ArithmeticOperation MULTIPLY        = new ArithmeticOperation(Opcode.MULTIPLY);
    public static final ArithmeticOperation DIVIDE          = new ArithmeticOperation(Opcode.DIVIDE);
    public static final ArithmeticOperation MODULUS         = new ArithmeticOperation(Opcode.MODULUS);
    public static final ArithmeticOperation MIN             = new ArithmeticOperation(Opcode.MIN);
    public static final ArithmeticOperation MAX             = new ArithmeticOperation(Opcode.MAX);

    protected static ArithmeticOperation fromBytes(final ByteArrayReader byteArrayReader) {
        if (! byteArrayReader.hasBytes()) { return null; }

        final byte opcodeByte = byteArrayReader.readByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final Opcode opcode = TYPE.getSubtype(opcodeByte);
        if (opcode == null) { return null; }

        return new ArithmeticOperation(opcode);
    }

    protected ArithmeticOperation(final Opcode opcode) {
        super(opcode.getValue(), TYPE, opcode);
    }

    @Override
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableTransactionContext context) {
        final MedianBlockTime medianBlockTime = context.getMedianBlockTime();
        final UpgradeSchedule upgradeSchedule = context.getUpgradeSchedule();

        if (! _opcode.isEnabled()) {
            Logger.debug("Opcode is disabled: " + _opcode);
            return false;
        }

        switch (_opcode) {
            case ADD_ONE: {
                final Value value = stack.pop();
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! value.isMinimallyEncoded()) { return false; }
                }
                if (! value.isWithinLongIntegerRange()) { return false; }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! value.isWithinIntegerRange()) { return false; }
                }

                final Long longValue = value.asLong();

                final Long newIntValue = MathUtil.add(longValue, 1L);
                if (newIntValue == null) { return false; }

                final Value newValue = Value.fromInteger(newIntValue);
                if (! newValue.isWithinLongIntegerRange()) { return false; }
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case SUBTRACT_ONE: {
                final Value value = stack.pop();
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! value.isMinimallyEncoded()) { return false; }
                }
                if (! value.isWithinLongIntegerRange()) { return false; }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! value.isWithinIntegerRange()) { return false; }
                }

                final Long longValue = value.asLong();

                final Long newIntValue = MathUtil.subtract(longValue, 1L);
                if (newIntValue == null) { return false; }

                final Value newValue = Value.fromInteger(newIntValue);
                if (! newValue.isWithinLongIntegerRange()) { return false; }

                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case MULTIPLY_BY_TWO: {
                final Value value = stack.pop();
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! value.isMinimallyEncoded()) { return false; }
                }
                if (! value.isWithinLongIntegerRange()) { return false; }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! value.isWithinIntegerRange()) { return false; }
                }

                final Long longValue = value.asLong();

                final Long newIntValue = MathUtil.multiply(longValue, 2L);
                if (newIntValue == null) { return false; }

                final Value newValue = Value.fromInteger(newIntValue);
                if (! newValue.isWithinLongIntegerRange()) { return false; }
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case DIVIDE_BY_TWO: {
                final Value value = stack.pop();
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! value.isMinimallyEncoded()) { return false; }
                }
                if (! value.isWithinLongIntegerRange()) { return false; }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! value.isWithinIntegerRange()) { return false; }
                }

                final Long longValue = value.asLong();

                final Long newIntValue = MathUtil.divide(longValue, 2L);
                if (newIntValue == null) { return false; }

                final Value newValue = Value.fromInteger(newIntValue);
                if (! newValue.isWithinLongIntegerRange()) { return false; }
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case NEGATE: {
                final Value value = stack.pop();
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! value.isMinimallyEncoded()) { return false; }
                }
                if (! value.isWithinLongIntegerRange()) { return false; }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! value.isWithinIntegerRange()) { return false; }
                }

                final Long longValue = value.asLong();

                final Long newIntValue = MathUtil.negate(longValue);
                if (newIntValue == null) { return false; }

                final Value newValue = Value.fromInteger(newIntValue);
                if (! newValue.isWithinLongIntegerRange()) { return false; }
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case ABSOLUTE_VALUE: {
                final Value value = stack.pop();
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! value.isMinimallyEncoded()) { return false; }
                }
                if (! value.isWithinLongIntegerRange()) { return false; }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! value.isWithinIntegerRange()) { return false; }
                }

                final Long longValue = value.asLong();

                final Long newIntValue = MathUtil.absoluteValue(longValue);
                if (newIntValue == null) { return false; }

                final Value newValue = Value.fromInteger(newIntValue);
                if (! newValue.isWithinLongIntegerRange()) { return false; }
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case NOT: {
                final Value value = stack.pop();
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! value.isMinimallyEncoded()) { return false; }
                }
                if (! value.isWithinLongIntegerRange()) { return false; }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! value.isWithinIntegerRange()) { return false; }
                }

                final Long longValue = value.asLong();

                final Long newLongValue = (longValue == 0L ? 1L : 0L);

                final Value newValue = Value.fromInteger(newLongValue);
                if (! newValue.isWithinLongIntegerRange()) { return false; }
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case ADD: {
                final Value value1 = stack.pop();
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! value1.isMinimallyEncoded()) { return false; }
                }
                if (! value1.isWithinLongIntegerRange()) { return false; }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! value1.isWithinIntegerRange()) { return false; }
                }

                final Value value0 = stack.pop();
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! value0.isMinimallyEncoded()) { return false; }
                }
                if (! value0.isWithinLongIntegerRange()) { return false; }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! value0.isWithinIntegerRange()) { return false; }
                }

                final Long longValue0 = value0.asLong();
                final Long longValue1 = value1.asLong();

                final Long newIntValue = MathUtil.add(longValue0, longValue1);
                if (newIntValue == null) { return false; }

                final Value newValue = Value.fromInteger(newIntValue);
                if (! newValue.isWithinLongIntegerRange()) { return false; }
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case SUBTRACT: {
                final Value value1 = stack.pop();
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! value1.isMinimallyEncoded()) { return false; }
                }
                if (! value1.isWithinLongIntegerRange()) { return false; }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! value1.isWithinIntegerRange()) { return false; }
                }

                final Value value0 = stack.pop();
                if (! value0.isWithinLongIntegerRange()) { return false; }
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! value0.isMinimallyEncoded()) { return false; }
                }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! value0.isWithinIntegerRange()) { return false; }
                }

                final Long longValue0 = value0.asLong();
                final Long longValue1 = value1.asLong();

                final Long newIntValue = MathUtil.subtract(longValue0, longValue1);
                if (newIntValue == null) { return false; }

                final Value newValue = Value.fromInteger(newIntValue);
                if (! newValue.isWithinLongIntegerRange()) { return false; }
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case MULTIPLY: {
                if (! upgradeSchedule.isMultiplyOperationEnabled(medianBlockTime)) { return false; }

                final Value value1 = stack.pop();
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! value1.isMinimallyEncoded()) { return false; }
                }
                if (! value1.isWithinLongIntegerRange()) { return false; }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! value1.isWithinIntegerRange()) { return false; }
                }

                final Value value0 = stack.pop();
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! value0.isMinimallyEncoded()) { return false; }
                }
                if (! value0.isWithinLongIntegerRange()) { return false; }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! value0.isWithinIntegerRange()) { return false; }
                }

                final Long longValue0 = value0.asLong();
                final Long longValue1 = value1.asLong();

                final Long value = MathUtil.multiply(longValue1, longValue0);
                if (value == null) { return false; }

                final Value newValue = Value.fromInteger(value);
                if (! newValue.isWithinLongIntegerRange()) { return false; }
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case DIVIDE: {
                // value0 value1 DIVIDE -> { value0 / value1 }
                // { 0x0A } { 0x02 } DIVIDE -> { 0x05 }

                final Value value1 = stack.pop(); // Divisor
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! value1.isMinimallyEncoded()) { return false; }
                }
                if (! value1.isWithinLongIntegerRange()) { return false; }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! value1.isWithinIntegerRange()) { return false; }
                }

                final Value value0 = stack.pop();
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! value0.isMinimallyEncoded()) { return false; }
                }
                if (! value0.isWithinLongIntegerRange()) { return false; }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! value0.isWithinIntegerRange()) { return false; }
                }

                final Long longValue0 = value0.asLong();
                final Long longValue1 = value1.asLong();

                if (longValue1 == 0) { return false; }

                final Long newIntValue = MathUtil.divide(longValue0, longValue1);
                if (newIntValue == null) { return false; }

                final Value newValue = Value.fromInteger(newIntValue);
                if (! newValue.isWithinLongIntegerRange()) { return false; }
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case MODULUS: {
                // value0 value1 MODULUS -> { value0 % value1 }
                // { 0x0A } { 0x02 } MODULUS -> { 0x00 }

                final Value value1 = stack.pop();
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! value1.isMinimallyEncoded()) { return false; }
                }
                if (! value1.isWithinLongIntegerRange()) { return false; }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! value1.isWithinIntegerRange()) { return false; }
                }

                final Value value0 = stack.pop();
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! value0.isMinimallyEncoded()) { return false; }
                }
                if (! value0.isWithinLongIntegerRange()) { return false; }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! value0.isWithinIntegerRange()) { return false; }
                }

                final Long longValue0 = value0.asLong();
                final Long longValue1 = value1.asLong();

                if (longValue1 == 0) { return false; }

                final Long newIntValue = (longValue0 % longValue1);

                final Value newValue = Value.fromInteger(newIntValue);
                if (! newValue.isWithinLongIntegerRange()) { return false; }

                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case MIN: {
                final Value value1 = stack.pop();
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! value1.isMinimallyEncoded()) { return false; }
                }
                if (! value1.isWithinLongIntegerRange()) { return false; }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! value1.isWithinIntegerRange()) { return false; }
                }

                final Value value0 = stack.pop();
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! value0.isMinimallyEncoded()) { return false; }
                }
                if (! value0.isWithinLongIntegerRange()) { return false; }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! value0.isWithinIntegerRange()) { return false; }
                }

                final Long longValue0 = value0.asLong();
                final Long longValue1 = value1.asLong();

                final Long newIntValue = Math.min(longValue1, longValue0);

                final Value newValue = Value.fromInteger(newIntValue);
                if (! newValue.isWithinLongIntegerRange()) { return false; }
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case MAX: {
                final Value value1 = stack.pop();
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! value1.isMinimallyEncoded()) { return false; }
                }
                if (! value1.isWithinLongIntegerRange()) { return false; }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! value1.isWithinIntegerRange()) { return false; }
                }

                final Value value0 = stack.pop();
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! value0.isMinimallyEncoded()) { return false; }
                }
                if (! value0.isWithinLongIntegerRange()) { return false; }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! value0.isWithinIntegerRange()) { return false; }
                }

                final Long longValue0 = value0.asLong();
                final Long longValue1 = value1.asLong();

                final Long newIntValue = Math.max(longValue1, longValue0);

                final Value newValue = Value.fromInteger(newIntValue);
                if (! newValue.isWithinLongIntegerRange()) { return false; }
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            default: { return false; }
        }
    }
}
