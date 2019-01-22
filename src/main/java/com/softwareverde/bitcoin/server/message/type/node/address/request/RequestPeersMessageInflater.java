package com.softwareverde.bitcoin.server.message.type.node.address.request;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class RequestPeersMessageInflater extends BitcoinProtocolMessageInflater {

    @Override
    public RequestPeersMessage fromBytes(final byte[] bytes) {
        final RequestPeersMessage requestPeersMessage = new RequestPeersMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.REQUEST_PEERS);
        if (protocolMessageHeader == null) { return null; }

        if (byteArrayReader.didOverflow()) { return null; }

        return requestPeersMessage;
    }
}
