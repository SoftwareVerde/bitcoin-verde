package com.softwareverde.bitcoin.server.message;

import com.softwareverde.bitcoin.server.message.header.ProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.header.ProtocolMessageHeaderParser;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.io.Logger;

public abstract class ProtocolMessageInflater {
    public abstract ProtocolMessage fromBytes(byte[] bytes);

    protected ProtocolMessageHeader _parseHeader(final ByteArrayReader byteArrayReader, final ProtocolMessage.MessageType command) {
        final ProtocolMessageHeaderParser protocolMessageHeaderParser = new ProtocolMessageHeaderParser();
        final ProtocolMessageHeader protocolMessageHeader = protocolMessageHeaderParser.fromBytes(byteArrayReader);

        { // Validate MessageType Type
            if (command != protocolMessageHeader.command) {
                return null;
            }
        }

        final Integer actualPayloadByteCount = byteArrayReader.remainingByteCount();
        { // Validate Payload Byte Count
            if (protocolMessageHeader.payloadByteCount != actualPayloadByteCount) {
                Logger.log("Bad Payload size. "+ protocolMessageHeader.payloadByteCount +" != "+ actualPayloadByteCount);
                return null;
            }
        }

        final byte[] payload = byteArrayReader.peakBytes(protocolMessageHeader.payloadByteCount, Endian.BIG);

        { // Validate Checksum
            final ByteArray calculatedChecksum = ProtocolMessage.calculateChecksum(MutableByteArray.wrap(payload));
            if (! ByteUtil.areEqual(protocolMessageHeader.payloadChecksum, calculatedChecksum.getBytes())) {
                return null;
            }
        }

        if (byteArrayReader.didOverflow()) { return null; }

        return protocolMessageHeader;
    }
}
