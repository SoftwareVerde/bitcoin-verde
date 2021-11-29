package com.softwareverde.bitcoin.transaction.script.opcode;

public abstract class SubTypedOperation extends Operation {
    protected final Opcode _opcode;

    protected SubTypedOperation(final byte value, final Type type, final Opcode opcode) {
        super(value, type);
        _opcode = opcode;
    }

    public Opcode getOpcode() {
        return _opcode;
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof SubTypedOperation)) { return false ;}
        if (! super.equals(object)) { return false; }

        final SubTypedOperation operation = (SubTypedOperation) object;
        if (operation._opcode != _opcode) { return false; }

        return true;
    }

    @Override
    public String toString() {
        return super.toString() + "-" + _opcode;
    }
}
