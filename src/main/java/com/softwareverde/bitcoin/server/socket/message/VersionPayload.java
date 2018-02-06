package com.softwareverde.bitcoin.server.socket.message;

import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteArrayBuilder.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.ByteArrayBuilder.Endian;
import com.softwareverde.bitcoin.util.ByteUtil;

public class VersionPayload {
    private final byte[] _version = new byte[4];
    private final byte[] _services = new byte[8];
    private final byte[] _timestamp = new byte[8];
    private final byte[] _remoteAddress = new byte[26];
    private final byte[] _localAddress = new byte[26];
    private final byte[] _nonce = new byte[8];
    private byte[] _userAgent = new byte[1];
    private final byte[] _currentBlockHeight = new byte[4];
    private final byte[] _shouldRelay = new byte[1];

    private void _setBytes(final byte[] destination, final byte[] value, final Integer offset) {
        for (int i=0; (i+offset)<destination.length; ++i) {
            destination[i + offset] = (i < value.length ? value[i] : 0x00);
        }
    }

    private void _setBytes(final byte[] destination, final byte[] value) {
        _setBytes(destination, value, 0);
    }

    public VersionPayload() {
        final Long epoch = (long) 0x000000005a79d419; // (System.currentTimeMillis() / 1000L);
        final Long nonce = 947735760792572705L; // (long) 0x0d2708a732f9d321; // (long) (Math.random() * Long.MAX_VALUE);

        _setBytes(_version, BitcoinUtil.hexStringToByteArray("0001117F")); // 0000EA62
        _setBytes(_services, ByteUtil.longToBytes(0L));
        _setBytes(_timestamp, ByteUtil.longToBytes(epoch));                                                                     // BitcoinUtil.hexStringToByteArray("0000000050D0B211"));
        _setBytes(_remoteAddress, BitcoinUtil.hexStringToByteArray("370000000000000000000000000000000000FFFF18C03CDC208D"));    // TODO // 010000000000000000000000000000000000FFFF18C03CDC208D
        _setBytes(_localAddress, BitcoinUtil.hexStringToByteArray("000000000000000000000000000000000000FFFF000000000000"));     // TODO // 010000000000000000000000000000000000FFFF0A0002FF208D
        _setBytes(_nonce, ByteUtil.longToBytes(nonce));                                                                         // BitcoinUtil.hexStringToByteArray("6517E68C5DB32E3B"));

        {
            final byte[] userAgentBytes = "/bitnodes.earn.com:0.1/".getBytes(); // "/Verde-Bitcoin:0.0.1/".getBytes();                                                   // /Satoshi:0.7.2/
            final byte[] userAgentBytesEncodedLength = ByteUtil.serializeVariableLengthInteger((long) userAgentBytes.length);
            _userAgent = new byte[userAgentBytesEncodedLength.length + userAgentBytes.length];
            _setBytes(_userAgent, userAgentBytesEncodedLength);
            _setBytes(_userAgent, userAgentBytes, userAgentBytesEncodedLength.length);
        }

        _setBytes(_currentBlockHeight, ByteUtil.integerToBytes(0x0007e075));
        _setBytes(_shouldRelay, BitcoinUtil.hexStringToByteArray("00"));
    }

    public byte[] getBytes() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(_version, Endian.LITTLE);
        byteArrayBuilder.appendBytes(_services, Endian.LITTLE);
        byteArrayBuilder.appendBytes(_timestamp, Endian.LITTLE);
        byteArrayBuilder.appendBytes(_remoteAddress, Endian.BIG);
        byteArrayBuilder.appendBytes(_localAddress, Endian.BIG);
        byteArrayBuilder.appendBytes(_nonce, Endian.LITTLE);
        byteArrayBuilder.appendBytes(_userAgent, Endian.BIG);
        byteArrayBuilder.appendBytes(_currentBlockHeight, Endian.LITTLE);
        byteArrayBuilder.appendBytes(_shouldRelay, Endian.LITTLE);
        return byteArrayBuilder.build();
    }
}
