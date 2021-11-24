package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableTransactionContext;
import com.softwareverde.bitcoin.transaction.script.runner.context.TransactionContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class ComparisonOperation extends SubTypedOperation {
    // NOTE: The order of the parameters is the top item being the modifier, and the the 2nd item being the base.
    //  For instance, pushing "2" then "1" on the stack results in [2, 1].
    //  In this context, for OP_SUB, 1 is subtracted from 2 (2-1), which results in 1 being put on the stack.

    public static final Type TYPE = Type.OP_COMPARISON;

    public static final ComparisonOperation IS_EQUAL                            = new ComparisonOperation(Opcode.IS_EQUAL);
    public static final ComparisonOperation IS_EQUAL_THEN_VERIFY                = new ComparisonOperation(Opcode.IS_EQUAL_THEN_VERIFY);
    public static final ComparisonOperation IS_TRUE                             = new ComparisonOperation(Opcode.IS_TRUE);
    public static final ComparisonOperation IS_NUMERICALLY_EQUAL                = new ComparisonOperation(Opcode.IS_NUMERICALLY_EQUAL);
    public static final ComparisonOperation IS_NUMERICALLY_EQUAL_THEN_VERIFY    = new ComparisonOperation(Opcode.IS_NUMERICALLY_EQUAL_THEN_VERIFY);
    public static final ComparisonOperation IS_NUMERICALLY_NOT_EQUAL            = new ComparisonOperation(Opcode.IS_NUMERICALLY_NOT_EQUAL);
    public static final ComparisonOperation IS_LESS_THAN                        = new ComparisonOperation(Opcode.IS_LESS_THAN);
    public static final ComparisonOperation IS_GREATER_THAN                     = new ComparisonOperation(Opcode.IS_GREATER_THAN);
    public static final ComparisonOperation IS_LESS_THAN_OR_EQUAL               = new ComparisonOperation(Opcode.IS_LESS_THAN_OR_EQUAL);
    public static final ComparisonOperation IS_GREATER_THAN_OR_EQUAL            = new ComparisonOperation(Opcode.IS_GREATER_THAN_OR_EQUAL);
    public static final ComparisonOperation INTEGER_AND                         = new ComparisonOperation(Opcode.INTEGER_AND);
    public static final ComparisonOperation INTEGER_OR                          = new ComparisonOperation(Opcode.INTEGER_OR);
    public static final ComparisonOperation IS_WITHIN_RANGE                     = new ComparisonOperation(Opcode.IS_WITHIN_RANGE);

    protected static ComparisonOperation fromBytes(final ByteArrayReader byteArrayReader) {
        if (! byteArrayReader.hasBytes()) { return null; }

        final byte opcodeByte = byteArrayReader.readByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final Opcode opcode = TYPE.getSubtype(opcodeByte);
        if (opcode == null) { return null; }

        return new ComparisonOperation(opcode);
    }

    protected ComparisonOperation(final Opcode opcode) {
        super(opcode.getValue(), TYPE, opcode);
    }

    protected Tuple<Long, Long> _popNumericTuple(final Stack stack, final TransactionContext context) {
        final UpgradeSchedule upgradeSchedule = context.getUpgradeSchedule();
        final MedianBlockTime medianBlockTime = context.getMedianBlockTime();

        final Value value0 = stack.pop();
        if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
            if (! value0.isMinimallyEncoded()) { return null; }
        }
        if (! value0.isWithinLongIntegerRange()) { return null; }
        if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
            if (! value0.isWithinIntegerRange()) { return null; }
        }

        final Value value1 = stack.pop();
        if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
            if (! value1.isMinimallyEncoded()) { return null; }
        }
        if (! value1.isWithinLongIntegerRange()) { return null; }
        if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
            if (! value1.isWithinIntegerRange()) { return null; }
        }

        if (stack.didOverflow()) { return null; }

        final Long longValue0 = value0.asLong();
        final Long longValue1 = value1.asLong();

        return new Tuple<>(longValue0, longValue1);
    }

    protected Boolean _opIsEqual(final Stack stack) {
        final Value value0 = stack.pop();
        final Value value1 = stack.pop();
        if (stack.didOverflow()) { return null; }

        return ByteUtil.areEqual(value0, value1);
    }

    protected Boolean _opIsNumericallyEqual(final Stack stack, final TransactionContext transactionContext) {
        final Tuple<Long, Long> numericTuple = _popNumericTuple(stack, transactionContext);
        if (numericTuple == null) { return null; }

        return Util.areEqual(numericTuple.first, numericTuple.second);
    }

    @Override
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableTransactionContext context) {
        final UpgradeSchedule upgradeSchedule = context.getUpgradeSchedule();
        final MedianBlockTime medianBlockTime = context.getMedianBlockTime();

        switch (_opcode) {
            case IS_EQUAL: {
                final Boolean areEqual = _opIsEqual(stack);
                if (areEqual == null) { return false; }

                stack.push(Value.fromBoolean(areEqual));
                return true;
            }

            case IS_EQUAL_THEN_VERIFY: {
                final Boolean areEqual = _opIsEqual(stack);
                if (areEqual == null) { return false; }

                return areEqual;
            }

            case IS_TRUE: {
                final Value value = stack.pop();
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! value.isMinimallyEncoded()) { return false; }
                }

                final Boolean booleanValue = value.asBoolean();
                stack.push(Value.fromBoolean(booleanValue));

                return (! stack.didOverflow());
            }

            case IS_NUMERICALLY_EQUAL: {
                final Boolean areEqual = _opIsNumericallyEqual(stack, context);
                if (areEqual == null) { return false; }

                stack.push(Value.fromBoolean(areEqual));
                return true;
            }

            case IS_NUMERICALLY_EQUAL_THEN_VERIFY: {
                final Boolean areEqual = _opIsNumericallyEqual(stack, context);
                if (areEqual == null) { return false; }

                return areEqual;
            }

            case IS_NUMERICALLY_NOT_EQUAL: {
                final Boolean areEqual = _opIsNumericallyEqual(stack, context);
                if (areEqual == null) { return false; }

                stack.push(Value.fromBoolean(! areEqual));
                return true;
            }

            case IS_LESS_THAN: {
                final Tuple<Long, Long> numericTuple = _popNumericTuple(stack, context);
                if (numericTuple == null) { return false; }

                final Boolean isLessThan = (numericTuple.second < numericTuple.first);
                stack.push(Value.fromBoolean(isLessThan));
                return true;
            }

            case IS_GREATER_THAN: {
                final Tuple<Long, Long> numericTuple = _popNumericTuple(stack, context);
                if (numericTuple == null) { return false; }

                final Boolean isGreaterThan = (numericTuple.second > numericTuple.first);
                stack.push(Value.fromBoolean(isGreaterThan));
                return true;
            }

            case IS_LESS_THAN_OR_EQUAL: {
                final Tuple<Long, Long> numericTuple = _popNumericTuple(stack, context);
                if (numericTuple == null) { return false; }

                final Boolean isLessThan = (numericTuple.second < numericTuple.first);
                final Boolean areEqual = Util.areEqual(numericTuple.second, numericTuple.first);
                stack.push(Value.fromBoolean(isLessThan || areEqual));
                return true;
            }

            case IS_GREATER_THAN_OR_EQUAL: {
                final Tuple<Long, Long> numericTuple = _popNumericTuple(stack, context);
                if (numericTuple == null) { return false; }

                final Boolean isGreaterThan = (numericTuple.second > numericTuple.first);
                final Boolean areEqual = Util.areEqual(numericTuple.second, numericTuple.first);
                stack.push (Value.fromBoolean(isGreaterThan || areEqual));
                return true;
            }

            case INTEGER_AND: {
                final Tuple<Long, Long> numericTuple = _popNumericTuple(stack, context);
                if (numericTuple == null) { return false; }

                final Boolean value = ( (numericTuple.second != 0L) && (numericTuple.first != 0L) );
                stack.push(Value.fromBoolean(value));
                return true;
            }

            case INTEGER_OR: {
                final Tuple<Long, Long> numericTuple = _popNumericTuple(stack, context);
                if (numericTuple == null) { return false; }

                final Boolean value = ( (numericTuple.second != 0L) || (numericTuple.first != 0L) );
                stack.push(Value.fromBoolean(value));
                return true;
            }

            case IS_WITHIN_RANGE: {
                // NOTE: Pushes true on the stack if the value is greater than or equal to the min, and less than the max.
                //  Assuming the oldest items on the stack are on the left, the parameters are defined on the stack as: [..., VALUE, MIN, MAX]
                final Value valueMax = stack.pop();
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! valueMax.isMinimallyEncoded()) { return false; }
                }
                if (! valueMax.isWithinLongIntegerRange()) { return false; }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! valueMax.isWithinIntegerRange()) { return false; }
                }

                final Value valueMin = stack.pop();
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! valueMin.isMinimallyEncoded()) { return false; }
                }
                if (! valueMin.isWithinLongIntegerRange()) { return false; }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! valueMin.isWithinIntegerRange()) { return false; }
                }

                final Value value = stack.pop();
                if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
                    if (! value.isMinimallyEncoded()) { return false; }
                }
                if (! value.isWithinLongIntegerRange()) { return false; }
                if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
                    if (! value.isWithinIntegerRange()) { return false; }
                }

                if (stack.didOverflow()) { return false; }

                final Long longMax = valueMax.asLong();
                final Long longMin = valueMin.asLong();
                final Long longValue = value.asLong();

                final Boolean resultValue = ((longValue >= longMin) && (longValue < longMax));
                stack.push(Value.fromBoolean(resultValue));
                return true;
            }

            default: { return false; }
        }
    }
}
