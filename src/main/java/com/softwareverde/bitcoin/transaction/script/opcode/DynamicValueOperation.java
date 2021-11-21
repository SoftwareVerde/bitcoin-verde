package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableTransactionContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class DynamicValueOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_DYNAMIC_VALUE;

    public static final DynamicValueOperation PUSH_STACK_SIZE               = new DynamicValueOperation(Opcode.PUSH_STACK_SIZE);
    public static final DynamicValueOperation COPY_1ST                      = new DynamicValueOperation(Opcode.COPY_1ST);
    public static final DynamicValueOperation COPY_NTH                      = new DynamicValueOperation(Opcode.COPY_NTH);
    public static final DynamicValueOperation COPY_2ND                      = new DynamicValueOperation(Opcode.COPY_2ND);
    public static final DynamicValueOperation COPY_2ND_THEN_1ST             = new DynamicValueOperation(Opcode.COPY_2ND_THEN_1ST);
    public static final DynamicValueOperation COPY_3RD_THEN_2ND_THEN_1ST    = new DynamicValueOperation(Opcode.COPY_3RD_THEN_2ND_THEN_1ST);
    public static final DynamicValueOperation COPY_4TH_THEN_3RD             = new DynamicValueOperation(Opcode.COPY_4TH_THEN_3RD);
    public static final DynamicValueOperation COPY_1ST_THEN_MOVE_TO_3RD     = new DynamicValueOperation(Opcode.COPY_1ST_THEN_MOVE_TO_3RD);

    protected static DynamicValueOperation fromBytes(final ByteArrayReader byteArrayReader) {
        if (! byteArrayReader.hasBytes()) { return null; }

        final byte opcodeByte = byteArrayReader.readByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final Opcode opcode = TYPE.getSubtype(opcodeByte);
        if (opcode == null) { return null; }

        return new DynamicValueOperation(opcode);
    }

    protected DynamicValueOperation(final Opcode opcode) {
        super(opcode.getValue(), TYPE, opcode);
    }

    @Override
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableTransactionContext context) {
        switch (_opcode) {
            case PUSH_STACK_SIZE: {
                stack.push(Value.fromInteger(stack.getSize().longValue()));
                return true;
            }

            case COPY_1ST: {
                stack.push(stack.peak());
                return (! stack.didOverflow());
            }

            case COPY_NTH: {
                final Value nValue = stack.pop();
                if (! Operation.validateMinimalEncoding(nValue, context)) { return false; }

                final Integer n = nValue.asInteger();
                stack.push(stack.peak(n));
                return (! stack.didOverflow());
            }

            case COPY_2ND: {
                final Value value = stack.peak(1);
                stack.push(value);
                return (! stack.didOverflow());
            }

            case COPY_2ND_THEN_1ST: {
                final Value value0 = stack.peak(0);
                final Value value1 = stack.peak(1);
                stack.push(value1);
                stack.push(value0);
                return (! stack.didOverflow());
            }

            case COPY_3RD_THEN_2ND_THEN_1ST: {
                final Value value0 = stack.peak(0);
                final Value value1 = stack.peak(1);
                final Value value2 = stack.peak(2);

                stack.push(value2);
                stack.push(value1);
                stack.push(value0);
                return (! stack.didOverflow());
            }

            case COPY_4TH_THEN_3RD: {
                final Value value2 = stack.peak(2);
                final Value value3 = stack.peak(3);

                stack.push(value3);
                stack.push(value2);
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
