package com.softwareverde.bitcoin.server.message.header;

import com.softwareverde.bitcoin.server.message.ProtocolMessage;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class ProtocolMessageHeaderParser {
    public static final Integer HEADER_BYTE_COUNT = 24;

    private ProtocolMessageHeader _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final byte[] magicNumber = byteArrayReader.readBytes(4, Endian.LITTLE);

        { // Validate Magic Number
            if (! ByteUtil.areEqual(ProtocolMessage.MAIN_NET_MAGIC_NUMBER, magicNumber)) { return null; }
        }

        final byte[] commandBytes = byteArrayReader.readBytes(12, Endian.BIG);
        final ProtocolMessage.MessageType command = ProtocolMessage.MessageType.fromBytes(commandBytes);

        final Integer payloadByteCount = byteArrayReader.readInteger(4, Endian.LITTLE);
        final byte[] payloadChecksum = byteArrayReader.readBytes(4, Endian.BIG);

        return new ProtocolMessageHeader(magicNumber, command, payloadByteCount, payloadChecksum);
    }

    public ProtocolMessageHeader fromBytes(final byte[] bytes) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        return _fromByteArrayReader(byteArrayReader);
    }

    public ProtocolMessageHeader fromBytes(final ByteArrayReader byteArrayReader) {
        return _fromByteArrayReader(byteArrayReader);
    }
}
