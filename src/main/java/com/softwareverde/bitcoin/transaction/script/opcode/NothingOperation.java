package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.reader.ScriptReader;
import com.softwareverde.bitcoin.transaction.script.runner.Context;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;

public class NothingOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_NOTHING;

    protected static NothingOperation fromScriptReader(final ScriptReader scriptReader) {
        if (! scriptReader.hasNextByte()) { return null; }

        final byte opcodeByte = scriptReader.getNextByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final SubType subType = TYPE.getSubtype(opcodeByte);
        if (subType == null) { return null; }

        return new NothingOperation(opcodeByte, subType);
    }

    protected NothingOperation(final byte value, final SubType subType) {
        super(value, TYPE, subType);
    }

    @Override
    public Boolean applyTo(final Stack stack, Context context) {
        return true;
    }
}
