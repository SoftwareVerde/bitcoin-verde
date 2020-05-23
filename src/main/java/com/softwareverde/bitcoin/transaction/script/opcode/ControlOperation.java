package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
import com.softwareverde.bitcoin.transaction.script.runner.context.TransactionContext;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableTransactionContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class ControlOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_CONTROL;

    public static final ControlOperation IF             = new ControlOperation(Opcode.IF.getValue(),                Opcode.IF);
    public static final ControlOperation NOT_IF         = new ControlOperation(Opcode.NOT_IF.getValue(),            Opcode.NOT_IF);
    public static final ControlOperation ELSE           = new ControlOperation(Opcode.ELSE.getValue(),              Opcode.ELSE);
    public static final ControlOperation END_IF         = new ControlOperation(Opcode.END_IF.getValue(),            Opcode.END_IF);
    public static final ControlOperation RETURN         = new ControlOperation(Opcode.RETURN.getValue(),            Opcode.RETURN);
    public static final ControlOperation VERIFY         = new ControlOperation(Opcode.VERIFY.getValue(),            Opcode.VERIFY);
    public static final ControlOperation IF_VERSION     = new ControlOperation(Opcode.IF_VERSION.getValue(),        Opcode.IF_VERSION);
    public static final ControlOperation IF_NOT_VERSION = new ControlOperation(Opcode.IF_NOT_VERSION.getValue(),    Opcode.IF_NOT_VERSION);

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
    public Boolean shouldExecute(final Stack stack, final ControlState controlState, final TransactionContext transactionContext) {
        // NOTE: IF, NOT_IF, ELSE, and END_IF are always "executed", but their encapsulated operations may not be...
        switch (_opcode) {
            case IF:
            case NOT_IF:
            case ELSE:
            case END_IF:
            case IF_VERSION:
            case IF_NOT_VERSION: {
                return true;
            }

            default: {
                return super.shouldExecute(stack, controlState, transactionContext);
            }
        }
    }

    @Override
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableTransactionContext context) {
        if (! _opcode.isEnabled()) {
            Logger.debug("Opcode is disabled: " + _opcode);
            return false;
        }

        switch (_opcode) {

            case IF:
            case NOT_IF: {
                final Boolean condition;
                if (controlState.shouldExecute()) {
                    final Value value = stack.pop();
                    final Boolean booleanValue = value.asBoolean();

                    final Boolean notIf = (_opcode == Opcode.NOT_IF);
                    condition = ( notIf ? (! booleanValue) : (booleanValue) );
                }
                else {
                    condition = false;
                }

                controlState.enteredIfBlock(condition);

                return (! stack.didOverflow());
            }

            case IF_VERSION:
            case IF_NOT_VERSION: {
                final Boolean condition;
                if (controlState.shouldExecute()) {
                    final Value value = stack.pop();
                    final String userAgent = value.asString();
                    final Boolean booleanValue = (BitcoinConstants.getUserAgent().equalsIgnoreCase(userAgent));

                    final Boolean notIf = (_opcode == Opcode.IF_NOT_VERSION);
                    condition = ( notIf ? (! booleanValue) : (booleanValue) );
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
