package com.softwareverde.bitcoin.server.message.type.version.acknowledge;

import com.softwareverde.bitcoin.server.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.message.header.ProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.ProtocolMessageInflater;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;

public class AcknowledgeVersionMessageInflater extends ProtocolMessageInflater {

    @Override
    public AcknowledgeVersionMessage fromBytes(final byte[] bytes) {
        final AcknowledgeVersionMessage synchronizeVersionMessage = new AcknowledgeVersionMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final ProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, ProtocolMessage.MessageType.ACKNOWLEDGE_VERSION);
        if (protocolMessageHeader == null) { return null; }

        return synchronizeVersionMessage;
    }

}
