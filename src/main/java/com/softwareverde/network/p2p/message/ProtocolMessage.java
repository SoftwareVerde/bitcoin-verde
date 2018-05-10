package com.softwareverde.network.p2p.message;

import com.softwareverde.constable.bytearray.ByteArray;

public interface ProtocolMessage<T> {
    ByteArray getMagicNumber();
    T getCommand();
    byte[] getHeaderBytes();
    ByteArray getBytes();
}
