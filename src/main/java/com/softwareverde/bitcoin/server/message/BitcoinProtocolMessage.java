package com.softwareverde.bitcoin.server.message;

import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeaderInflater;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.network.socket.BinaryPacketFormat;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.ImmutableByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.util.HexUtil;

/**
 * Protocol Definition:
 *  https://bitcoin.org/en/developer-reference
 *  https://en.bitcoin.it/wiki/Protocol_documentation
 */

public abstract class BitcoinProtocolMessage implements ProtocolMessage {
    public static final ByteArray MAIN_NET_MAGIC_NUMBER = new ImmutableByteArray(HexUtil.hexStringToByteArray("E8F3E1E3")); // NOTICE: Different Network Magic-Number for Bitcoin Cash.  Bitcoin Core expects: D9B4BEF9.  Discovered via Bitcoin-ABC source code.
    public static final BinaryPacketFormat BINARY_PACKET_FORMAT = new BinaryPacketFormat(BitcoinProtocolMessage.MAIN_NET_MAGIC_NUMBER, new BitcoinProtocolMessageHeaderInflater(), new BitcoinProtocolMessageInflater());

    protected static final Integer CHECKSUM_BYTE_COUNT = 4;

    public static ByteArray calculateChecksum(final ByteArray payload) {
        final ByteArray fullChecksum = BitcoinUtil.sha256(BitcoinUtil.sha256(payload));
        final MutableByteArray checksum = new MutableByteArray(4);

        for (int i = 0; i< CHECKSUM_BYTE_COUNT; ++i) {
            checksum.set(i, fullChecksum.getByte(i));
        }

        return checksum;
    }

    protected final ByteArray _magicNumber;
    protected final MessageType _command;

    public BitcoinProtocolMessage(final MessageType command) {
        _magicNumber = MAIN_NET_MAGIC_NUMBER;
        _command = command;
    }

    private ByteArray _getBytes() {
        final ByteArray payload = _getPayload();

        final byte[] payloadSizeBytes = ByteUtil.integerToBytes(payload.getByteCount());
        final ByteArray checksum = BitcoinProtocolMessage.calculateChecksum(payload);

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        byteArrayBuilder.appendBytes(_magicNumber, Endian.LITTLE);
        byteArrayBuilder.appendBytes(_command.getBytes(), Endian.BIG);
        byteArrayBuilder.appendBytes(payloadSizeBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(checksum.getBytes(), Endian.BIG); // NOTICE: Bitcoin Cash wants the checksum to be big-endian.  Bitcoin Core documentation says little-endian.  Discovered via tcpdump on server.
        byteArrayBuilder.appendBytes(payload.getBytes(), Endian.BIG);

        return MutableByteArray.wrap(byteArrayBuilder.build());
    }

    protected abstract ByteArray _getPayload();

    public ByteArray getMagicNumber() {
        return _magicNumber;
    }

    public MessageType getCommand() {
        return _command;
    }

    public byte[] getHeaderBytes() {
        return ByteUtil.copyBytes(_getBytes().getBytes(), 0, BitcoinProtocolMessageHeaderInflater.HEADER_BYTE_COUNT);
    }

    @Override
    public ByteArray getBytes() {
        return _getBytes();
    }
}
