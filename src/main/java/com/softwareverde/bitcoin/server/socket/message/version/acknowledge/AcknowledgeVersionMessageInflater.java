package com.softwareverde.bitcoin.server.socket.message.version.acknowledge;

import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageHeader;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageHeaderParser;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageInflater;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class AcknowledgeVersionMessageInflater implements ProtocolMessageInflater {

    @Override
    public AcknowledgeVersionMessage fromBytes(final byte[] bytes) {
        final AcknowledgeVersionMessage synchronizeVersionMessage = new AcknowledgeVersionMessage();
        final ProtocolMessageHeaderParser protocolMessageHeaderParser = new ProtocolMessageHeaderParser();

        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        final ProtocolMessageHeader protocolMessageHeader = protocolMessageHeaderParser.fromBytes(byteArrayReader);

        { // Validate Command Type
            if (ProtocolMessage.Command.ACKNOWLEDGE_VERSION != protocolMessageHeader.command) {
                return null;
            }
        }

        final Integer actualPayloadByteCount = byteArrayReader.remainingByteCount();
        { // Validate Payload Byte Count
            if (protocolMessageHeader.payloadByteCount != actualPayloadByteCount) {
                System.out.println("Bad Payload size. "+ protocolMessageHeader.payloadByteCount +" != "+ actualPayloadByteCount);
                return null;
            }
        }

        final byte[] payload = byteArrayReader.peakBytes(protocolMessageHeader.payloadByteCount, Endian.BIG);

        { // Validate Checksum
            final byte[] calculatedChecksum = ProtocolMessage.calculateChecksum(payload);
            if (! ByteUtil.areEqual(protocolMessageHeader.payloadChecksum, calculatedChecksum)) {
                return null;
            }
        }

        return synchronizeVersionMessage;
    }

}
