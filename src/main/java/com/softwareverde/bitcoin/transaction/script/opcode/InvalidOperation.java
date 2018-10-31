package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class InvalidOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_INVALID;

    protected static InvalidOperation fromBytes(final ByteArrayReader byteArrayReader) {
        if (! byteArrayReader.hasBytes()) { return null; }

        final byte opcodeByte = byteArrayReader.readByte();

        return new InvalidOperation(opcodeByte);
    }

    protected InvalidOperation(final byte value) {
        super(value, TYPE, null);
    }

    @Override
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableContext context) {
        Logger.log("NOTICE: Attempted to execute an unknown opcode: 0x" + HexUtil.toHexString(new byte[] { _opcode.getValue() }));
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
