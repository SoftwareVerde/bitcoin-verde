package com.softwareverde.bitcoin.server.socket.message;

import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.bitcoin.util.ByteUtil;

/**
 * Protocol Definition: https://en.bitcoin.it/wiki/Protocol_documentation
 */

public abstract class ProtocolMessage {
    public static final byte[] MAIN_NET_MAGIC_NUMBER = BitcoinUtil.hexStringToByteArray("E8F3E1E3"); // NOTICE: Different Network Magic-Number for Bitcoin Cash.  Bitcoin Core expects: D9B4BEF9.  Discovered via Bitcoin-ABC source code.
    protected static final Integer CHECKSUM_BYTE_COUNT = 4;

    public enum MessageType {
        SYNCHRONIZE_VERSION("version"), ACKNOWLEDGE_VERSION("verack"),
        PING("ping"), PONG("pong"),
        PEER_ADDRESSES("addr"),
        GET_BLOCK_HEADERS("getheaders"), GET_BLOCKS("getblocks"), INVENTORY("inv"),
        REJECT("reject");

        public static MessageType fromBytes(final byte[] bytes) {
            for (final MessageType command : MessageType.values()) {
                if (ByteUtil.areEqual(command._bytes, bytes)) {
                    return command;
                }
            }
            return null;
        }

        private final byte[] _bytes = new byte[12];
        private final String _value;

        MessageType(final String value) {
            _value = value;
            final byte[] valueBytes = value.getBytes();

            for (int i=0; i<_bytes.length; ++i) {
                _bytes[i] = (i<valueBytes.length ? valueBytes[i] : 0x00);
            }
        }

        public byte[] getBytes() {
            return ByteUtil.copyBytes(_bytes);
        }

        public String getValue() {
            return _value;
        }
    }

    public static byte[] calculateChecksum(final byte[] payload) {
        final byte[] fullChecksum = BitcoinUtil.sha256(BitcoinUtil.sha256(payload));
        final byte[] checksum = new byte[4];

        for (int i = 0; i< CHECKSUM_BYTE_COUNT; ++i) {
            checksum[i] = fullChecksum[i];
        }

        return checksum;
    }

    protected final byte[] _magicNumber = MAIN_NET_MAGIC_NUMBER;
    protected final MessageType _command;

    protected abstract byte[] _getPayload();

    private byte[] _getBytes() {
        final byte[] payload = _getPayload();

        final byte[] payloadSizeBytes = ByteUtil.integerToBytes(payload.length);
        final byte[] checksum = ProtocolMessage.calculateChecksum(payload);

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        byteArrayBuilder.appendBytes(_magicNumber, Endian.LITTLE);
        byteArrayBuilder.appendBytes(_command.getBytes(), Endian.BIG);
        byteArrayBuilder.appendBytes(payloadSizeBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(checksum, Endian.BIG); // NOTICE: Bitcoin Cash wants the checksum to be big-endian.  Bitcoin Core documentation says little-endian.  Discovered via tcpdump on server.
        byteArrayBuilder.appendBytes(payload, Endian.BIG);

        return byteArrayBuilder.build();
    }

    public ProtocolMessage(final MessageType command) {
        _command = command;
    }

    public byte[] getMagicNumber() {
        return ByteUtil.copyBytes(_magicNumber);
    }

    public MessageType getCommand() {
        return _command;
    }

    public byte[] getHeaderBytes() {
        return ByteUtil.copyBytes(_getBytes(), 0, ProtocolMessageHeaderParser.HEADER_BYTE_COUNT);
    }

    public byte[] getBytes() {
        return _getBytes();
    }
}
