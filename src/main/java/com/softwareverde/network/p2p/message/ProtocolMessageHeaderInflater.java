package com.softwareverde.network.p2p.message;

import com.softwareverde.util.bytearray.ByteArrayReader;

public interface ProtocolMessageHeaderInflater {
    Integer getHeaderByteCount();
    Integer getMaxPacketByteCount(ProtocolMessageHeader protocolMessageHeader);
    ProtocolMessageHeader fromBytes(final byte[] bytes);
    ProtocolMessageHeader fromBytes(final ByteArrayReader byteArrayReader);
}