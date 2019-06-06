package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class ComparisonOperation extends SubTypedOperation {
    // NOTE: The order of the parameters is the top item being the modifier, and the the 2nd item being the base.
    //  For instance, pushing "2" then "1" on the stack results in [2, 1].
    //  In this context, for OP_SUB, 1 is subtracted from 2 (2-1), which results in 1 being put on the stack.

    public static final Type TYPE = Type.OP_COMPARISON;

    public static final ComparisonOperation IS_EQUAL                            = new ComparisonOperation(Opcode.IS_EQUAL.getValue(),                           Opcode.IS_EQUAL);
    public static final ComparisonOperation IS_EQUAL_THEN_VERIFY                = new ComparisonOperation(Opcode.IS_EQUAL_THEN_VERIFY.getValue(),               Opcode.IS_EQUAL_THEN_VERIFY);
    public static final ComparisonOperation IS_TRUE                             = new ComparisonOperation(Opcode.IS_TRUE.getValue(),                            Opcode.IS_TRUE);
    public static final ComparisonOperation IS_NUMERICALLY_EQUAL                = new ComparisonOperation(Opcode.IS_NUMERICALLY_EQUAL.getValue(),               Opcode.IS_NUMERICALLY_EQUAL);
    public static final ComparisonOperation IS_NUMERICALLY_EQUAL_THEN_VERIFY    = new ComparisonOperation(Opcode.IS_NUMERICALLY_EQUAL_THEN_VERIFY.getValue(),   Opcode.IS_NUMERICALLY_EQUAL_THEN_VERIFY);
    public static final ComparisonOperation IS_NUMERICALLY_NOT_EQUAL            = new ComparisonOperation(Opcode.IS_NUMERICALLY_NOT_EQUAL.getValue(),           Opcode.IS_NUMERICALLY_NOT_EQUAL);
    public static final ComparisonOperation IS_LESS_THAN                        = new ComparisonOperation(Opcode.IS_LESS_THAN.getValue(),                       Opcode.IS_LESS_THAN);
    public static final ComparisonOperation IS_GREATER_THAN                     = new ComparisonOperation(Opcode.IS_GREATER_THAN.getValue(),                    Opcode.IS_GREATER_THAN);
    public static final ComparisonOperation IS_LESS_THAN_OR_EQUAL               = new ComparisonOperation(Opcode.IS_LESS_THAN_OR_EQUAL.getValue(),              Opcode.IS_LESS_THAN_OR_EQUAL);
    public static final ComparisonOperation IS_GREATER_THAN_OR_EQUAL            = new ComparisonOperation(Opcode.IS_GREATER_THAN_OR_EQUAL.getValue(),           Opcode.IS_GREATER_THAN_OR_EQUAL);
    public static final ComparisonOperation INTEGER_AND                         = new ComparisonOperation(Opcode.INTEGER_AND.getValue(),                        Opcode.INTEGER_AND);
    public static final ComparisonOperation INTEGER_OR                          = new ComparisonOperation(Opcode.INTEGER_OR.getValue(),                         Opcode.INTEGER_OR);
    public static final ComparisonOperation IS_WITHIN_RANGE                     = new ComparisonOperation(Opcode.IS_WITHIN_RANGE.getValue(),                    Opcode.IS_WITHIN_RANGE);

    protected static ComparisonOperation fromBytes(final ByteArrayReader byteArrayReader) {
        if (! byteArrayReader.hasBytes()) { return null; }

        final byte opcodeByte = byteArrayReader.readByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final Opcode opcode = TYPE.getSubtype(opcodeByte);
        if (opcode == null) { return null; }

        return new ComparisonOperation(opcodeByte, opcode);
    }

    protected ComparisonOperation(final byte value, final Opcode opcode) {
        super(value, TYPE, opcode);
    }

    protected Boolean _opIsEqual(final Stack stack) {
        final Value value0 = stack.pop();
        final Value value1 = stack.pop();
        if (stack.didOverflow()) { return false; }

        return ByteUtil.areEqual(value0.getBytes(), value1.getBytes());
    }

    protected Boolean _opIsNumericallyEqual(final Stack stack) {
        final Value value0 = stack.pop();
        final Value value1 = stack.pop();
        if (stack.didOverflow()) { return false; }

        final Boolean areEqual = (value1.asInteger().intValue() == value0.asInteger().intValue());
        return (areEqual);
    }

    @Override
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableContext context) {
        switch (_opcode) {
            case IS_EQUAL: {
                final Boolean isEqual = _opIsEqual(stack);
                stack.push(Value.fromBoolean(isEqual));
                return (! stack.didOverflow());
            }

            case IS_EQUAL_THEN_VERIFY: {
                final Boolean areEqual = _opIsEqual(stack);
                if (stack.didOverflow()) { return false; }
                return areEqual;
            }

            case IS_TRUE: {
                final Value value = stack.pop();
                final Boolean booleanValue = value.asBoolean();
                stack.push(Value.fromBoolean(booleanValue));
                return (! stack.didOverflow());
            }

            case IS_NUMERICALLY_EQUAL: {
                final Boolean isEqual = _opIsNumericallyEqual(stack);
                stack.push(Value.fromBoolean(isEqual));
                return (! stack.didOverflow());
            }

            case IS_NUMERICALLY_EQUAL_THEN_VERIFY: {
                final Boolean areEqual = _opIsNumericallyEqual(stack);
                if (stack.didOverflow()) { return false; }
                return areEqual;
            }

            case IS_NUMERICALLY_NOT_EQUAL: {
                final Boolean isEqual = _opIsNumericallyEqual(stack);
                stack.push(Value.fromBoolean(! isEqual));
                return (! stack.didOverflow());
            }

            case IS_LESS_THAN: {
                final Value value0 = stack.pop();
                final Value value1 = stack.pop();

                final Boolean isLessThan = (value1.asInteger() < value0.asInteger());
                stack.push(Value.fromBoolean(isLessThan));
                return (! stack.didOverflow());
            }

            case IS_GREATER_THAN: {
                final Value value0 = stack.pop();
                final Value value1 = stack.pop();

                final Boolean isGreaterThan = (value1.asInteger() > value0.asInteger());
                stack.push(Value.fromBoolean(isGreaterThan));
                return (! stack.didOverflow());
            }

            case IS_LESS_THAN_OR_EQUAL: {
                final Value value0 = stack.pop();
                final Value value1 = stack.pop();

                final Integer valueInteger0 = value0.asInteger();
                final Integer valueInteger1 = value1.asInteger();

                final Boolean isLessThan = (valueInteger1 < valueInteger0);
                final Boolean areEqual = (valueInteger0.intValue() == valueInteger1.intValue());
                stack.push(Value.fromBoolean( (isLessThan) || (areEqual) ));
                return (! stack.didOverflow());
            }

            case IS_GREATER_THAN_OR_EQUAL: {
                final Value value0 = stack.pop();
                final Value value1 = stack.pop();

                final Integer valueInteger0 = value0.asInteger();
                final Integer valueInteger1 = value1.asInteger();

                final Boolean isGreaterThan = (valueInteger1 > valueInteger0);
                final Boolean areEqual = (valueInteger1.intValue() == valueInteger0.intValue());
                stack.push (Value.fromBoolean( (isGreaterThan) || (areEqual) ));
                return (! stack.didOverflow());
            }

            case INTEGER_AND: {
                final Value value0 = stack.pop();
                final Value value1 = stack.pop();

                final int intValue0 = value0.asInteger();
                final int intValue1 = value1.asInteger();

                final Boolean value = ((intValue1 != 0) && (intValue0 != 0));
                stack.push(Value.fromBoolean(value));

                return (! stack.didOverflow());
            }

            case INTEGER_OR: {
                final Value value0 = stack.pop();
                final Value value1 = stack.pop();

                final int intValue0 = value0.asInteger();
                final int intValue1 = value1.asInteger();

                final Boolean value = ((intValue1 != 0) || (intValue0 != 0));
                stack.push(Value.fromBoolean(value));

                return (! stack.didOverflow());
            }

            case IS_WITHIN_RANGE: {
                // NOTE: Pushes true on the stack if the value is greater than or equal to the min, and less than the max.
                //  Assuming the oldest items on the stack are on the left, the parameters are defined on the stack as: [..., VALUE, MIN, MAX]
                final Value valueMax = stack.pop();
                final Value valueMin = stack.pop();
                final Value value = stack.pop();

                final int intValueMax = valueMax.asInteger();
                final int intValueMin = valueMin.asInteger();
                final int intValue = value.asInteger();

                final Boolean resultValue = ((intValue >= intValueMin) || (intValue < intValueMax));
                stack.push(Value.fromBoolean(resultValue));

                return (! stack.didOverflow());
            }

            default: { return false; }
        }
    }
}
