package com.softwareverde.bitcoin.server.message;

import com.softwareverde.bitcoin.server.message.header.ProtocolMessageHeaderParser;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.HexUtil;

/**
 * Protocol Definition:
 *  https://bitcoin.org/en/developer-reference
 *  https://en.bitcoin.it/wiki/Protocol_documentation
 */

public abstract class ProtocolMessage {
    public static final byte[] MAIN_NET_MAGIC_NUMBER = HexUtil.hexStringToByteArray("E8F3E1E3"); // NOTICE: Different Network Magic-Number for Bitcoin Cash.  Bitcoin Core expects: D9B4BEF9.  Discovered via Bitcoin-ABC source code.
    protected static final Integer CHECKSUM_BYTE_COUNT = 4;

    public enum MessageType {
        SYNCHRONIZE_VERSION("version"), ACKNOWLEDGE_VERSION("verack"),
        PING("ping"), PONG("pong"),
        NODE_ADDRESSES("addr"),
        QUERY_BLOCK_HEADERS("getheaders"), QUERY_BLOCKS("getblocks"), QUERY_RESPONSE("inv"), REQUEST_OBJECT("getdata"),
        ERROR("reject"),
        BLOCK("block");

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

    public static ByteArray calculateChecksum(final ByteArray payload) {
        final ByteArray fullChecksum = BitcoinUtil.sha256(BitcoinUtil.sha256(payload));
        final MutableByteArray checksum = new MutableByteArray(4);

        for (int i = 0; i< CHECKSUM_BYTE_COUNT; ++i) {
            checksum.set(i, fullChecksum.getByte(i));
        }

        return checksum;
    }

    protected final byte[] _magicNumber = MAIN_NET_MAGIC_NUMBER;
    protected final MessageType _command;

    protected abstract ByteArray _getPayload();

    private ByteArray _getBytes() {
        final ByteArray payload = _getPayload();

        final byte[] payloadSizeBytes = ByteUtil.integerToBytes(payload.getByteCount());
        final ByteArray checksum = ProtocolMessage.calculateChecksum(payload);

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        byteArrayBuilder.appendBytes(_magicNumber, Endian.LITTLE);
        byteArrayBuilder.appendBytes(_command.getBytes(), Endian.BIG);
        byteArrayBuilder.appendBytes(payloadSizeBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(checksum.getBytes(), Endian.BIG); // NOTICE: Bitcoin Cash wants the checksum to be big-endian.  Bitcoin Core documentation says little-endian.  Discovered via tcpdump on server.
        byteArrayBuilder.appendBytes(payload.getBytes(), Endian.BIG);

        return MutableByteArray.wrap(byteArrayBuilder.build());
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
        return ByteUtil.copyBytes(_getBytes().getBytes(), 0, ProtocolMessageHeaderParser.HEADER_BYTE_COUNT);
    }

    public ByteArray getBytes() {
        return _getBytes();
    }
}
