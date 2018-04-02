package com.softwareverde.bitcoin.transaction.script.opcode;

public abstract class SubTypedOperation extends Operation {
    protected final SubType _subType;

    protected SubTypedOperation(final byte value, final Type type, final SubType subType) {
        super(value, type);
        _subType = subType;
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof SubTypedOperation)) { return false ;}
        if (! super.equals(object)) { return false; }

        final SubTypedOperation operation = (SubTypedOperation) object;
        if (operation._subType != _subType) { return false; }

        return true;
    }

    @Override
    public String toString() {
        return super.toString() + " - " + _subType;
    }
}
