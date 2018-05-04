package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.io.Logger;

public class ControlOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_CONTROL;

    protected static ControlOperation fromBytes(final ByteArrayReader byteArrayReader) {
        if (! byteArrayReader.hasBytes()) { return null; }

        final byte opcodeByte = byteArrayReader.readByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final Opcode opcode = TYPE.getSubtype(opcodeByte);
        if (opcode == null) { return null; }

        return new ControlOperation(opcodeByte, opcode);
    }

    protected ControlOperation(final byte value, final Opcode opcode) {
        super(value, TYPE, opcode);
    }

    @Override
    public Boolean shouldExecute(final Stack stack, final ControlState controlState, final MutableContext context) {
        // NOTE: IF, NOT_IF, ELSE, and END_IF are always "executed", but their encapsulated operations may not be...
        switch (_opcode) {
            case IF:
            case NOT_IF:
            case ELSE:
            case END_IF: {
                return true;
            }

            default: {
                return super.shouldExecute(stack, controlState, context);
            }
        }
    }

    @Override
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableContext context) {
        context.incrementCurrentLockingScriptIndex();

        // NOTE: Currently, no ControlOperations are disabled...
        if (! _opcode.isEnabled()) {
            Logger.log("NOTICE: Opcode is disabled: " + _opcode);
            return false;
        }

        switch (_opcode) {

            case IF: {
                final Boolean condition;
                if (controlState.shouldExecute()) {
                    final Value value = stack.pop();
                    condition = value.asBoolean();
                }
                else {
                    condition = false;
                }

                controlState.enteredIfBlock(condition);

                return (! stack.didOverflow());
            }

            case NOT_IF: {
                final Boolean condition;
                if (controlState.shouldExecute()) {
                    final Value value = stack.pop();
                    condition = (! value.asBoolean());
                }
                else {
                    condition = false;
                }

                controlState.enteredIfBlock(condition);

                return (! stack.didOverflow());
            }

            case ELSE: {
                // NOTE: Having multiple Else-CodeBlocks is valid.
                //  The following script is legal, and validates to true:
                //
                //  OP_1
                //  OP_IF           //                      (CodeBlock Pushed, shouldExecute=True)
                //      OP_1        // Is executed...
                //  OP_ELSE         //                      (CodeBlock Replaced, shouldExecute=False)
                //      OP_RETURN   // Is NOT executed..
                //  OP_ELSE         //                      (CodeBlock Replaced, shouldExecute=True)
                //      OP_1        // Is executed...
                //  OP_ELSE         //                      (CodeBlock Replaced, shouldExecute=False)
                //      OP_RETURN   // Is NOT executed...
                //  OP_ENDIF        //                      (CodeBlock Popped)
                //

                if (! controlState.isInCodeBlock()) { return false; }
                controlState.enteredElseBlock();
                return true;
            }

            case END_IF: {
                if (! controlState.isInCodeBlock()) { return false; }
                controlState.exitedCodeBlock();
                return true;
            }

            case VERIFY: {
                final Value value = stack.pop();
                final Boolean booleanValue = value.asBoolean();
                if (! booleanValue) { return false; }
                return (! stack.didOverflow());
            }

            case RETURN: {
                return false;
            }

            default: { return false; }
        }
    }
}
