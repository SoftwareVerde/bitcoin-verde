package com.softwareverde.bitcoin.server.socket.message.version.acknowledge;

import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageHeader;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageHeaderParser;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageInflater;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class AcknowledgeVersionMessageInflater extends ProtocolMessageInflater {

    @Override
    public AcknowledgeVersionMessage fromBytes(final byte[] bytes) {
        final AcknowledgeVersionMessage synchronizeVersionMessage = new AcknowledgeVersionMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final ProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, ProtocolMessage.Command.ACKNOWLEDGE_VERSION);
        if (protocolMessageHeader == null) { return null; }

        return synchronizeVersionMessage;
    }

}
