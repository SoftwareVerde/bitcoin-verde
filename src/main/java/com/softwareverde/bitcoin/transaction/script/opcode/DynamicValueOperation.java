package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.runner.Context;
import com.softwareverde.bitcoin.transaction.script.runner.ScriptRunner;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;

public class DynamicValueOperation extends Operation {
    public static final Type TYPE = Type.OP_DYNAMIC_VALUE;

    public static DynamicValueOperation fromScript(final Script script) {
        if (! script.hasNextByte()) { return null; }

        final byte opcodeByte = script.getNextByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final SubType subType = TYPE.getSubtype(opcodeByte);
        if (subType == null) { return null; }

        return new DynamicValueOperation(opcodeByte, subType);
    }

    protected final SubType _subType;

    protected DynamicValueOperation(final byte value, final SubType subType) {
        super(value, TYPE);
        _subType = subType;
    }

    @Override
    public Boolean applyTo(final Stack stack, final Context context) {
        switch (_subType) {
            case PUSH_STACK_SIZE: {
                stack.push(Value.fromInteger(stack.getSize()));
                return true;
            }

            case COPY_1ST: {
                stack.push(stack.peak());
                return (! stack.didOverflow());
            }

            case COPY_NTH: {
                final Value nValue = stack.pop();
                final Integer n = nValue.asInteger();

                stack.push(stack.peak(n));
                return (! stack.didOverflow());
            }

            case COPY_2ND: {
                stack.push(stack.peak(1));
                return (! stack.didOverflow());
            }

            case COPY_2ND_THEN_1ST: {
                stack.push(stack.peak(1));
                stack.push(stack.peak(0));
                return (! stack.didOverflow());
            }

            case COPY_3RD_THEN_2ND_THEN_1ST: {
                stack.push(stack.peak(2));
                stack.push(stack.peak(1));
                stack.push(stack.peak(0));
                return (! stack.didOverflow());
            }

            case COPY_4TH_THEN_3RD: {
                stack.push(stack.peak(3));
                stack.push(stack.peak(2));
                return (! stack.didOverflow());
            }

            case COPY_1ST_THEN_MOVE_TO_3RD: {
                                                                // 4 3 2 1
                final Value copiedValue = stack.peak();
                final Value firstValue = stack.pop();           // 4 3 2
                final Value secondValue = stack.pop();          // 4 3
                stack.push(copiedValue);                        // 4 3 1
                stack.push(secondValue);                        // 4 3 1 2
                stack.push(firstValue);                         // 4 3 1 2 1
                return (! stack.didOverflow());
            }

            default: { return false; }
        }
    }
}
