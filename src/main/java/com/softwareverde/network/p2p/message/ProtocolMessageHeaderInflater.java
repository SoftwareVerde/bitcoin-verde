package com.softwareverde.network.p2p.message;

import com.softwareverde.util.bytearray.ByteArrayReader;

public interface ProtocolMessageHeaderInflater<T> {
    Integer getHeaderByteCount();
    T fromBytes(final byte[] bytes);
    T fromBytes(final ByteArrayReader byteArrayReader);
}