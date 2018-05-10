package com.softwareverde.bitcoin.server.message.type.node.ping;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class PingMessageInflater extends BitcoinProtocolMessageInflater {

    @Override
    public PingMessage fromBytes(final byte[] bytes) {
        final PingMessage pingMessage = new PingMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.PING);
        if (protocolMessageHeader == null) { return null; }

        pingMessage._nonce = byteArrayReader.readLong(8, Endian.LITTLE);

        if (byteArrayReader.didOverflow()) { return null; }

        return pingMessage;
    }
}
