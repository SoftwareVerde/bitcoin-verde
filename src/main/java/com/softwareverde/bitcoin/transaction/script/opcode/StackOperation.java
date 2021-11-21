package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableTransactionContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class StackOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_STACK;

    public static final StackOperation POP_TO_ALT_STACK             = new StackOperation(Opcode.POP_TO_ALT_STACK);
    public static final StackOperation POP_FROM_ALT_STACK           = new StackOperation(Opcode.POP_FROM_ALT_STACK);
    public static final StackOperation IF_1ST_TRUE_THEN_COPY_1ST    = new StackOperation(Opcode.IF_1ST_TRUE_THEN_COPY_1ST);
    public static final StackOperation POP                          = new StackOperation(Opcode.POP);
    public static final StackOperation REMOVE_2ND_FROM_TOP          = new StackOperation(Opcode.REMOVE_2ND_FROM_TOP);
    public static final StackOperation MOVE_NTH_TO_1ST              = new StackOperation(Opcode.MOVE_NTH_TO_1ST);
    public static final StackOperation ROTATE_TOP_3                 = new StackOperation(Opcode.ROTATE_TOP_3);
    public static final StackOperation SWAP_1ST_WITH_2ND            = new StackOperation(Opcode.SWAP_1ST_WITH_2ND);
    public static final StackOperation POP_THEN_POP                 = new StackOperation(Opcode.POP_THEN_POP);
    public static final StackOperation MOVE_5TH_AND_6TH_TO_TOP      = new StackOperation(Opcode.MOVE_5TH_AND_6TH_TO_TOP);
    public static final StackOperation SWAP_1ST_2ND_WITH_3RD_4TH    = new StackOperation(Opcode.SWAP_1ST_2ND_WITH_3RD_4TH);

    protected static StackOperation fromBytes(final ByteArrayReader byteArrayReader) {
        if (! byteArrayReader.hasBytes()) { return null; }

        final byte opcodeByte = byteArrayReader.readByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final Opcode opcode = TYPE.getSubtype(opcodeByte);
        if (opcode == null) { return null; }

        return new StackOperation(opcode);
    }

    protected StackOperation(final Opcode opcode) {
        super(opcode.getValue(), TYPE, opcode);
    }

    @Override
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableTransactionContext context) {
        switch (_opcode) {
            case POP_TO_ALT_STACK: {
                final Value value = stack.pop();
                stack.pushToAltStack(value);
                return (! stack.didOverflow());
            }
            case POP_FROM_ALT_STACK: {
                final Value value = stack.popFromAltStack();
                stack.push(value);
                return (! stack.didOverflow());
            }
            case IF_1ST_TRUE_THEN_COPY_1ST: {
                final Value value = stack.peak();
                if (value.asBoolean()) {
                    stack.push(value);
                }
                return (! stack.didOverflow());
            }
            case POP: {
                stack.pop();
                return (! stack.didOverflow());
            }
            case REMOVE_2ND_FROM_TOP: {
                final Value keptValue = stack.pop();
                final Value droppedValue = stack.pop();
                stack.push(keptValue);
                return (! stack.didOverflow());
            }
            case MOVE_NTH_TO_1ST: {
                final Value nthIndexValue = stack.pop();
                if (! Operation.validateMinimalEncoding(nthIndexValue, context)) { return false; }
                if (! nthIndexValue.isWithinIntegerRange()) { return false; }

                final Integer nthIndex = nthIndexValue.asInteger();
                final Value nthItem = stack.pop(nthIndex);
                stack.push(nthItem);

                return (! stack.didOverflow());
            }
            case ROTATE_TOP_3: {
                // Where the head is the leftmost element...
                // 0 1 2 3  // Initial state.
                //       3
                //     1 3
                //   0 1 3
                // 2 0 1 3  // End state.

                final Value valueAtZero = stack.pop();
                final Value valueAtOne = stack.pop();
                final Value valueAtTwo = stack.pop();

                stack.push(valueAtOne);
                stack.push(valueAtZero);
                stack.push(valueAtTwo);

                return (! stack.didOverflow());
            }
            case SWAP_1ST_WITH_2ND: {
                final Value secondItem = stack.pop(1);
                stack.push(secondItem);
                return (! stack.didOverflow());
            }
            case POP_THEN_POP: {
                stack.pop();
                stack.pop();
                return (! stack.didOverflow());
            }
            case MOVE_5TH_AND_6TH_TO_TOP: {
                // Where the head is the leftmost element...
                // 0 1 2 3 4 5 6 // Initial state.

                // 0 1 2 3[4]5 6 // 1. Remove element at 4 (value "4")
                // 0 1 2 3[5]6   // 2. Remove element at 4 (value "5") (was at 5)
                //  (5)0 1 2 3 6 // 3. Push element that was at 5.
                //(4)5 0 1 2 3 6 // 4. Push element that was at 4.

                // 4 5 0 1 2 3 6 // End state.

                final Value valueAtFour = stack.pop(4);
                final Value valueAtFive = stack.pop(4);
                stack.push(valueAtFive);
                stack.push(valueAtFour);

                return (! stack.didOverflow());
            }
            case SWAP_1ST_2ND_WITH_3RD_4TH: {
                // Where the head is the leftmost element...
                // 0 1 2 3 4 5 6 // Initial state.

                // 0 1[2]3 4 5 6 // 1. Remove element at 2 (value "2")
                // 0 1[3]4 5 6   // 2. Remove element at 2 (value "3") (was at 3)
                //  (3)0 1 2 3 6 // 3. Push element that was at 3.
                //(2)3 0 1 4 5 6 // 4. Push element that was at 2.

                // 2 3 0 1 4 5 6// End state.

                final Value valueAtTwo = stack.pop(2);
                final Value valueAtThree = stack.pop(2);
                stack.push(valueAtThree);
                stack.push(valueAtTwo);

                return (! stack.didOverflow());
            }

            default: { return false; }
        }
    }
}
