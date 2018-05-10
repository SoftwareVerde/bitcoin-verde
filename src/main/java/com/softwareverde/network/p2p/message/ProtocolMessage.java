package com.softwareverde.network.p2p.message;

import com.softwareverde.constable.bytearray.ByteArray;

public abstract class ProtocolMessage<T> {

    protected final ByteArray _magicNumber;
    protected final T _command;

    protected abstract ByteArray _getPayload();

    public ProtocolMessage(final T command, final ByteArray magicNumber) {
        _command = command;
        _magicNumber = magicNumber.asConst();
    }

    public ByteArray getMagicNumber() {
        return _magicNumber;
    }

    public T getCommand() {
        return _command;
    }

    public abstract byte[] getHeaderBytes();

    public abstract ByteArray getBytes();
}
