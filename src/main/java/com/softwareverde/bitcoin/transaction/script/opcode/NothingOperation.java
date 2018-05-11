package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.io.Logger;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class NothingOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_NOTHING;

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
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableContext context) {
        if (! _opcode.isEnabled()) {
            Logger.log("NOTICE: Opcode is disabled: " + _opcode);
            return false;
        }

        return true;
    }
}
