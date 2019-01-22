package com.softwareverde.bitcoin.server.message;

import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeaderInflater;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.io.Logger;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public abstract class BitcoinProtocolMessageInflater {
    public abstract BitcoinProtocolMessage fromBytes(byte[] bytes);

    protected BitcoinProtocolMessageHeader _parseHeader(final ByteArrayReader byteArrayReader, final MessageType command) {
        final BitcoinProtocolMessageHeaderInflater protocolMessageHeaderParser = new BitcoinProtocolMessageHeaderInflater();
        final BitcoinProtocolMessageHeader protocolMessageHeader = protocolMessageHeaderParser.fromBytes(byteArrayReader);

        { // Validate MessageType Type
            if (command != protocolMessageHeader.command) {
                Logger.log("ProtocolMessage: Command mismatch.");
                return null;
            }
        }

        final Integer actualPayloadByteCount = byteArrayReader.remainingByteCount();
        { // Validate Payload Byte Count
            if (protocolMessageHeader.payloadByteCount != actualPayloadByteCount) {
                Logger.log("ProtocolMessage: Bad payload size. "+ protocolMessageHeader.payloadByteCount +" != "+ actualPayloadByteCount);
                return null;
            }
        }

        final byte[] payload = byteArrayReader.peakBytes(protocolMessageHeader.payloadByteCount, Endian.BIG);

        { // Validate Checksum
            final ByteArray calculatedChecksum = BitcoinProtocolMessage.calculateChecksum(MutableByteArray.wrap(payload));
            if (! ByteUtil.areEqual(protocolMessageHeader.payloadChecksum, calculatedChecksum.getBytes())) {
                Logger.log("ProtocolMessage: Bad message checksum.");
                return null;
            }
        }

        if (byteArrayReader.didOverflow()) {
            Logger.log("ProtocolMessage: Buffer overflow.");
            return null;
        }

        return protocolMessageHeader;
    }
}

