package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.reader.ScriptReader;
import com.softwareverde.bitcoin.transaction.script.runner.Context;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.util.ByteUtil;

public class ComparisonOperation extends Operation {
    public static final Type TYPE = Type.OP_COMPARISON;

    protected static ComparisonOperation fromScriptReader(final ScriptReader scriptReader) {
        if (! scriptReader.hasNextByte()) { return null; }

        final byte opcodeByte = scriptReader.getNextByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final SubType subType = TYPE.getSubtype(opcodeByte);
        if (subType == null) { return null; }

        return new ComparisonOperation(opcodeByte, subType);
    }

    protected final SubType _subType;

    protected ComparisonOperation(final byte value, final SubType subType) {
        super(value, TYPE);
        _subType = subType;
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

        final Boolean areEqual = (value0.asInteger().intValue() == value1.asInteger().intValue());
        return (areEqual);
    }

    @Override
    public Boolean applyTo(final Stack stack, Context context) {
        switch (_subType) {
            case IS_EQUAL: {
                final Boolean isEqual = _opIsEqual(stack);
                stack.push(Value.fromBoolean(isEqual));
                return true;
            }

            case IS_EQUAL_THEN_VERIFY: {
                return _opIsEqual(stack);
            }

            case IS_FALSE: {
                final Boolean isEqual = _opIsEqual(stack);
                stack.push(Value.fromBoolean(! isEqual));
                return true;
            }

            case IS_NUMERICALLY_EQUAL: {
                final Boolean isEqual = _opIsNumericallyEqual(stack);
                stack.push(Value.fromBoolean(isEqual));
                return true;
            }

            case IS_NUMERICALLY_EQUAL_THEN_VERIFY: {
                return _opIsNumericallyEqual(stack);
            }

            case IS_NUMERICALLY_NOT_EQUAL: {
                final Boolean isEqual = _opIsNumericallyEqual(stack);
                stack.push(Value.fromBoolean(! isEqual));
                return true;
            }

            case IS_LESS_THAN: {
                final Value value0 = stack.pop();
                final Value value1 = stack.pop();
                if (stack.didOverflow()) { return false; }

                final Boolean isLessThan = (value0.asInteger() < value1.asInteger());
                stack.push (Value.fromBoolean(isLessThan));
                return true;
            }

            case IS_GREATER_THAN: {
                final Value value0 = stack.pop();
                final Value value1 = stack.pop();
                if (stack.didOverflow()) { return false; }

                final Boolean isGreaterThan = (value0.asInteger() > value1.asInteger());
                stack.push (Value.fromBoolean(isGreaterThan));
                return true;
            }

            case IS_LESS_THAN_OR_EQUAL: {
                final Value value0 = stack.pop();
                final Value value1 = stack.pop();
                if (stack.didOverflow()) { return false; }

                final Integer valueInteger0 = value0.asInteger();
                final Integer valueInteger1 = value1.asInteger();

                final Boolean isLessThan = (valueInteger0 < valueInteger1);
                final Boolean areEqual = (valueInteger0.intValue() == valueInteger1.intValue());
                stack.push (Value.fromBoolean(isLessThan || areEqual));
                return true;
            }

            case IS_GREATER_THAN_OR_EQUAL: {
                final Value value0 = stack.pop();
                final Value value1 = stack.pop();
                if (stack.didOverflow()) { return false; }

                final Integer valueInteger0 = value0.asInteger();
                final Integer valueInteger1 = value1.asInteger();

                final Boolean isGreaterThan = (valueInteger0 > valueInteger1);
                final Boolean areEqual = (valueInteger0.intValue() == valueInteger1.intValue());
                stack.push (Value.fromBoolean(isGreaterThan || areEqual));
                return true;
            }

            case IS_WITHIN_RANGE:
            default: { return false; }
        }
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof ComparisonOperation)) { return false ;}
        if (! super.equals(object)) { return false; }

        final ComparisonOperation operation = (ComparisonOperation) object;
        if (operation._subType != _subType) { return false; }

        return true;
    }
}
