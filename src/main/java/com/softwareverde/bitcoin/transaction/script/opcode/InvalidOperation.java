package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableTransactionContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class InvalidOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_INVALID;

    protected static InvalidOperation fromBytes(final ByteArrayReader byteArrayReader, final Boolean failIfPresent) {
        if (! byteArrayReader.hasBytes()) { return null; }

        final byte opcodeByte = byteArrayReader.readByte();

        return new InvalidOperation(opcodeByte, failIfPresent);
    }

    protected final Boolean _failIfPresent;

    protected InvalidOperation(final byte value, final Boolean failIfPresent) {
        super(value, TYPE, null);
        _failIfPresent = failIfPresent;
    }

    @Override
    public Boolean failIfPresent() {
        if (_failIfPresent) { return true; }
        return super.failIfPresent();
    }

    @Override
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableTransactionContext context) {
        Logger.debug("NOTICE: Attempted to execute an unknown opcode: 0x" + HexUtil.toHexString(new byte[] { _opcodeByte }));
        return false;
    }

    @Override
    public byte[] getBytes() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendByte(_opcodeByte);
        return byteArrayBuilder.build();
    }

    @Override
    public boolean equals(final Object object) {
        final Boolean superEquals = super.equals(object);
        if (! superEquals) { return false; }

        if (! (object instanceof InvalidOperation)) { return false; }
        if (! super.equals(object)) { return false; }

        return true;
    }

    @Override
    public String toString() {
        return (_type + " - 0x" + HexUtil.toHexString(this.getBytes()));
    }

    @Override
    public String toStandardString() {
        return "0x" + HexUtil.toHexString(this.getBytes());
    }
}
