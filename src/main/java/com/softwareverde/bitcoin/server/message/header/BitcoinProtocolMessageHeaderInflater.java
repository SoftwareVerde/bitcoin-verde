package com.softwareverde.bitcoin.server.message.header;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.message.ProtocolMessageHeaderInflater;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;
import sun.rmi.runtime.Log;

public class BitcoinProtocolMessageHeaderInflater implements ProtocolMessageHeaderInflater {
    public static final Integer MAX_PACKET_SIZE = 33554432; // 0x02000000
    public static final Integer HEADER_BYTE_COUNT = 24;

    private BitcoinProtocolMessageHeader _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final byte[] magicNumber = byteArrayReader.readBytes(4, Endian.LITTLE);

        { // Validate Magic Number
            if (! ByteUtil.areEqual(BitcoinProtocolMessage.MAIN_NET_MAGIC_NUMBER, MutableByteArray.wrap(magicNumber))) {
                Logger.log("Invalid Packet Magic Number: " + MutableByteArray.wrap(magicNumber));
                return null;
            }
        }

        final byte[] commandBytes = byteArrayReader.readBytes(12, Endian.BIG);
        final MessageType command = MessageType.fromBytes(commandBytes);

        final Integer payloadByteCount = byteArrayReader.readInteger(4, Endian.LITTLE);
        final byte[] payloadChecksum = byteArrayReader.readBytes(4, Endian.BIG);

        return new BitcoinProtocolMessageHeader(magicNumber, command, payloadByteCount, payloadChecksum);
    }

    @Override
    public Integer getHeaderByteCount() {
        return HEADER_BYTE_COUNT;
    }

    @Override
    public Integer getMaxPacketByteCount() {
        return MAX_PACKET_SIZE;
    }

    @Override
    public BitcoinProtocolMessageHeader fromBytes(final byte[] bytes) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        return _fromByteArrayReader(byteArrayReader);
    }

    @Override
    public BitcoinProtocolMessageHeader fromBytes(final ByteArrayReader byteArrayReader) {
        return _fromByteArrayReader(byteArrayReader);
    }
}
