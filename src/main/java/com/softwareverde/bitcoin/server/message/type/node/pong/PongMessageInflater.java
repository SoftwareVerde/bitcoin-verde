package com.softwareverde.bitcoin.server.message.type.node.pong;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class PongMessageInflater extends BitcoinProtocolMessageInflater {

    @Override
    public PongMessage fromBytes(final byte[] bytes) {
        final PongMessage pingMessage = new PongMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.PONG);
        if (protocolMessageHeader == null) { return null; }

        pingMessage._nonce = byteArrayReader.readLong(8, Endian.LITTLE);

        if (byteArrayReader.didOverflow()) { return null; }

        return pingMessage;
    }
}
