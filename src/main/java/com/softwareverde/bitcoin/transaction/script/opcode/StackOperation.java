package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class StackOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_STACK;

    protected static StackOperation fromBytes(final ByteArrayReader byteArrayReader) {
        if (! byteArrayReader.hasBytes()) { return null; }

        final byte opcodeByte = byteArrayReader.readByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final Opcode opcode = TYPE.getSubtype(opcodeByte);
        if (opcode == null) { return null; }

        return new StackOperation(opcodeByte, opcode);
    }

    protected StackOperation(final byte value, final Opcode opcode) {
        super(value, TYPE, opcode);
    }

    @Override
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableContext context) {
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
                final Integer nthIndex = stack.pop().asInteger();
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
