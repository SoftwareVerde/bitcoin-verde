package com.softwareverde.bitcoin.server.socket.message;

import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteArrayBuilder.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.ByteArrayBuilder.Endian;
import com.softwareverde.bitcoin.util.ByteUtil;

/**
 * Protocol Definition: https://en.bitcoin.it/wiki/Protocol_documentation
 */

public class ProtocolMessage {
    public static final byte[] MAIN_NET_MAGIC_NUMBER = BitcoinUtil.hexStringToByteArray("E8F3E1E3"); // NOTICE: Different Network Magic-Number for Bitcoin Cash.  Bitcoin Core expects: D9B4BEF9.  Discovered via Bitcoin-ABC source code.

    private static final Integer CHECKSUM_BYTE_COUNT = 4;
    private static final Integer PAYLOAD_SIZE_BYTE_COUNT = 4;

    public enum Command {
        SUBMIT_VERSION("version"), ACKNOWLEDGE_VERSION("verack");

        private final byte[] _bytes = new byte[12];
        Command(final String value) {
            final byte[] valueBytes = value.getBytes();

            for (int i=0; i<_bytes.length; ++i) {
                _bytes[i] = (i<valueBytes.length ? valueBytes[i] : 0x00);
            }
        }
    }

    protected final byte[] _magicNumber = MAIN_NET_MAGIC_NUMBER;
    protected final Command _command;
    protected byte[] _payload;

    protected byte[] _calculateChecksum() {

        if (_payload.length == 0) { return new byte[0]; }

        final byte[] fullChecksum = BitcoinUtil.sha256(BitcoinUtil.sha256(_payload));
        final byte[] checksum = new byte[4];

        for (int i = 0; i< CHECKSUM_BYTE_COUNT; ++i) {
            checksum[i] = fullChecksum[i];
        }

        return checksum;
    }

    public ProtocolMessage(final Command command) {
        _command = command;
        _payload = new byte[0];
    }

    public ProtocolMessage(final Command command, final byte[] payload) {
        _command = command;
        _payload = payload;
    }

    public void setPayload(final byte[] bytes) {
        _payload = new byte[bytes.length];
        for (int i=0; i<bytes.length; ++i) {
            _payload[i] = bytes[i];
        }
    }

    public byte[] serializeAsLittleEndian() {
        final byte[] payloadSizeBytes = ByteUtil.integerToBytes(_payload.length);
        final byte[] checksum = _calculateChecksum();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        byteArrayBuilder.appendBytes(_magicNumber, Endian.LITTLE);
        byteArrayBuilder.appendBytes(_command._bytes, Endian.BIG);
        byteArrayBuilder.appendBytes(payloadSizeBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(checksum, Endian.BIG); // NOTICE: Bitcoin Cash wants the checksum to be big-endian.  Bitcoin Core documentation says little-endian.  Discovered via tcpdump on server.
        byteArrayBuilder.appendBytes(_payload, Endian.BIG);

        return byteArrayBuilder.build();
    }
}
