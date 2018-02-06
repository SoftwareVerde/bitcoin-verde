package com.softwareverde.bitcoin.server.socket.message;

import com.softwareverde.bitcoin.server.socket.Constants;
import com.softwareverde.bitcoin.server.socket.message.networkaddress.NetworkAddress;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteArrayBuilder.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.ByteArrayBuilder.Endian;
import com.softwareverde.bitcoin.util.ByteUtil;

public class VersionPayload {
    public static final Integer VERSION = 0x0001117F;
    public static class ByteData {
        public final byte[] version = new byte[4];
        public final byte[] services = new byte[8];
        public final byte[] timestamp = new byte[8];
        public final byte[] remoteAddress = new byte[26];
        public final byte[] localAddress = new byte[26];
        public final byte[] nonce = new byte[8];
        public byte[] userAgent = new byte[1];
        public final byte[] currentBlockHeight = new byte[4];
        public final byte[] shouldRelay = new byte[1];
    }

    private final Integer _version = VERSION;
    private BitcoinServiceType _serviceType = BitcoinServiceType.NETWORK;
    private final Long _timestamp;
    private final NetworkAddress _remoteAddress = new NetworkAddress();
    private final NetworkAddress _localAddress = new NetworkAddress();
    private final Long _nonce;
    private Integer _currentBlockHeight;
    private Boolean _shouldRelay = false;

    private ByteData _createByteData() {
        final ByteData byteData = new ByteData();

        ByteUtil.setBytes(byteData.version, ByteUtil.integerToBytes(_version));
        ByteUtil.setBytes(byteData.services, ByteUtil.longToBytes(_serviceType.getValue()));
        ByteUtil.setBytes(byteData.timestamp, ByteUtil.longToBytes(_timestamp));
        ByteUtil.setBytes(byteData.remoteAddress, _remoteAddress.getBytes());  // BitcoinUtil.hexStringToByteArray("370000000000000000000000000000000000FFFF18C03CDC208D"));    // TODO // 010000000000000000000000000000000000FFFF18C03CDC208D
        ByteUtil.setBytes(byteData.localAddress, _localAddress.getBytes());    // BitcoinUtil.hexStringToByteArray("000000000000000000000000000000000000FFFF000000000000"));     // TODO // 010000000000000000000000000000000000FFFF0A0002FF208D
        ByteUtil.setBytes(byteData.nonce, ByteUtil.longToBytes(_nonce));

        { // Construct User-Agent bytes...
            final byte[] userAgentBytes = Constants.USER_AGENT.getBytes();
            final byte[] userAgentBytesEncodedLength = ByteUtil.serializeVariableLengthInteger((long) userAgentBytes.length);
            byteData.userAgent = new byte[userAgentBytesEncodedLength.length + userAgentBytes.length];
            ByteUtil.setBytes(byteData.userAgent, userAgentBytesEncodedLength);
            ByteUtil.setBytes(byteData.userAgent, userAgentBytes, userAgentBytesEncodedLength.length);
        }

        ByteUtil.setBytes(byteData.currentBlockHeight, ByteUtil.integerToBytes(_currentBlockHeight));

        { // Construct Should-Relay bytes...
            final String hexString = (_shouldRelay ? "01" : "00");
            final byte[] newBytesValue = BitcoinUtil.hexStringToByteArray(hexString);
            ByteUtil.setBytes(byteData.shouldRelay, newBytesValue);
        }

        return byteData;
    }

    public VersionPayload() {
        _timestamp = (System.currentTimeMillis() / 1000L);
        _nonce = (long) (Math.random() * Long.MAX_VALUE);
        _currentBlockHeight = 0;
    }

    public Integer getVersion() { return _version; }
    public BitcoinServiceType getServiceType() { return _serviceType; }
    public Long getTimestamp() { return _timestamp; }
    public NetworkAddress getLocalAddress() { return _localAddress; }
    public NetworkAddress getRemoteAddress() { return _remoteAddress; }
    public Long getNonce() { return _nonce; }
    public Boolean shouldRelay() { return _shouldRelay; }

    public void setServiceType(final BitcoinServiceType bitcoinServiceType) {
        _serviceType = bitcoinServiceType;
    }

    public void setLocalAddress(final NetworkAddress networkAddress) {
        _localAddress.copyFrom(networkAddress);
    }

    public void setRemoteAddress(final NetworkAddress networkAddress) {
        _remoteAddress.copyFrom(networkAddress);
    }

    public void setCurrentBlockHeight(final Integer currentBlockHeight) {
        _currentBlockHeight = currentBlockHeight;
    }

    public void setShouldRelay(final Boolean shouldRelay) {
        _shouldRelay = shouldRelay;
    }

    public byte[] getBytes() {
        final ByteData byteData = _createByteData();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(byteData.version, Endian.LITTLE);
        byteArrayBuilder.appendBytes(byteData.services, Endian.LITTLE);
        byteArrayBuilder.appendBytes(byteData.timestamp, Endian.LITTLE);
        byteArrayBuilder.appendBytes(byteData.remoteAddress, Endian.BIG);
        byteArrayBuilder.appendBytes(byteData.localAddress, Endian.BIG);
        byteArrayBuilder.appendBytes(byteData.nonce, Endian.LITTLE);
        byteArrayBuilder.appendBytes(byteData.userAgent, Endian.BIG);
        byteArrayBuilder.appendBytes(byteData.currentBlockHeight, Endian.LITTLE);
        byteArrayBuilder.appendBytes(byteData.shouldRelay, Endian.LITTLE);
        return byteArrayBuilder.build();
    }
}
