package com.softwareverde.bitcoin.server.socket.message;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public abstract class ProtocolMessageInflater {
    public abstract ProtocolMessage fromBytes(byte[] bytes);

    protected ProtocolMessageHeader _parseHeader(final ByteArrayReader byteArrayReader, final ProtocolMessage.Command command) {
        final ProtocolMessageHeaderParser protocolMessageHeaderParser = new ProtocolMessageHeaderParser();
        final ProtocolMessageHeader protocolMessageHeader = protocolMessageHeaderParser.fromBytes(byteArrayReader);

        { // Validate Command Type
            if (command != protocolMessageHeader.command) {
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

        return protocolMessageHeader;
    }
}
