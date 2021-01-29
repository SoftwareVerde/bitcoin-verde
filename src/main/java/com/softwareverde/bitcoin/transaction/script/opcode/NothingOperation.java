package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableTransactionContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class NothingOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_NOTHING;

    public static final NothingOperation NOTHING_OPERATION = new NothingOperation(Opcode.NO_OPERATION.getValue(), Opcode.NO_OPERATION);

    protected static NothingOperation fromBytes(final ByteArrayReader byteArrayReader) {
        if (! byteArrayReader.hasBytes()) { return null; }

        final byte opcodeByte = byteArrayReader.readByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final Opcode opcode = TYPE.getSubtype(opcodeByte);
        if (opcode == null) { return null; }

        return new NothingOperation(opcodeByte, opcode);
    }

    protected NothingOperation(final byte value, final Opcode opcode) {
        super(value, TYPE, opcode);
    }

    @Override
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableTransactionContext context) {
        if (! _opcode.isEnabled()) {
            Logger.debug("NOTICE: Opcode is disabled: " + _opcode);
            return false;
        }

        return true;
    }
}
