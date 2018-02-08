package com.softwareverde.bitcoin.server.socket.message.networkaddress.version.synchronize;

import com.softwareverde.bitcoin.server.Constants;
import com.softwareverde.bitcoin.server.socket.message.NodeFeatures;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.socket.message.networkaddress.NetworkAddress;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class SynchronizeVersionMessage extends ProtocolMessage {
    public static SynchronizeVersionMessage fromBytes(final byte[] bytes) {
        final SynchronizeVersionMessage synchronizeVersionMessage = new SynchronizeVersionMessage();

        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final byte[] magicNumber = byteArrayReader.readBytes(4, Endian.LITTLE);

        { // Validate Magic Number
            if (! ByteUtil.areEqual(MAIN_NET_MAGIC_NUMBER, magicNumber)) {
                return null;
            }
        }

        final byte[] commandBytes = byteArrayReader.readBytes(12, Endian.BIG);
        { // Validate Command Type
            if (! ByteUtil.areEqual(Command.SYNCHRONIZE_VERSION.getBytes(), commandBytes)) {
                return null;
            }
        }

        final Integer payloadByteCount = byteArrayReader.readInteger(4, Endian.LITTLE);
        final byte[] payloadChecksum = byteArrayReader.readBytes(4, Endian.BIG);

        final Integer actualPayloadByteCount = byteArrayReader.remainingByteCount();
        { // Validate Payload Byte Count
            if (payloadByteCount.intValue() != actualPayloadByteCount.intValue()) {
                return null;
            }
        }

        final byte[] payload = byteArrayReader.peakBytes(payloadByteCount, Endian.BIG);

        { // Validate Checksum
            final byte[] calculatedChecksum = _calculateChecksum(payload);
            if (! ByteUtil.areEqual(payloadChecksum, calculatedChecksum)) {
                return null;
            }
        }

        synchronizeVersionMessage._version = byteArrayReader.readInteger(4, Endian.LITTLE);

        final Long nodeFeatureFlags = byteArrayReader.readLong(8, Endian.LITTLE);
        synchronizeVersionMessage._nodeFeatures.setFeatureFlags(nodeFeatureFlags);

        synchronizeVersionMessage._timestamp = byteArrayReader.readLong(8, Endian.LITTLE);

        final byte[] remoteNetworkAddressBytes = byteArrayReader.readBytes(26, Endian.BIG);
        synchronizeVersionMessage._remoteNetworkAddress = NetworkAddress.fromBytes(remoteNetworkAddressBytes);

        final byte[] localNetworkAddressBytes = byteArrayReader.readBytes(26, Endian.BIG);
        synchronizeVersionMessage._localNetworkAddress = NetworkAddress.fromBytes(localNetworkAddressBytes);

        synchronizeVersionMessage._nonce = byteArrayReader.readLong(8, Endian.LITTLE);
        synchronizeVersionMessage._userAgent = byteArrayReader.readVariableLengthString();
        synchronizeVersionMessage._currentBlockHeight = byteArrayReader.readInteger(4, Endian.LITTLE);
        synchronizeVersionMessage._relayIsEnabled = byteArrayReader.readBoolean();
        return synchronizeVersionMessage;
    }

    public static final Integer VERSION = 0x0001117F;

    private static class ByteData {
        public final byte[] version = new byte[4];
        public final byte[] nodeFeatures = new byte[8];
        public final byte[] timestamp = new byte[8];
        public final byte[] remoteAddress = new byte[26];
        public final byte[] localAddress = new byte[26];
        public final byte[] nonce = new byte[8];
        public byte[] userAgent = new byte[1];
        public final byte[] currentBlockHeight = new byte[4];
        public final byte[] shouldRelay = new byte[1];
    }

    private Integer _version;
    private String _userAgent;
    private final NodeFeatures _nodeFeatures = new NodeFeatures();
    private Long _timestamp;
    private NetworkAddress _remoteNetworkAddress;
    private NetworkAddress _localNetworkAddress;
    private Long _nonce;
    private Integer _currentBlockHeight;
    private Boolean _relayIsEnabled = false;

    private ByteData _createByteData() {
        final ByteData byteData = new ByteData();

        ByteUtil.setBytes(byteData.version, ByteUtil.integerToBytes(_version));
        ByteUtil.setBytes(byteData.nodeFeatures, ByteUtil.longToBytes(_nodeFeatures.getFeatureFlags()));
        ByteUtil.setBytes(byteData.timestamp, ByteUtil.longToBytes(_timestamp));
        ByteUtil.setBytes(byteData.remoteAddress, _remoteNetworkAddress.getBytesWithoutTimestamp());
        ByteUtil.setBytes(byteData.localAddress, _localNetworkAddress.getBytesWithoutTimestamp());
        ByteUtil.setBytes(byteData.nonce, ByteUtil.longToBytes(_nonce));

        { // Construct User-Agent bytes...
            final byte[] userAgentBytes = _userAgent.getBytes();
            final byte[] userAgentBytesEncodedLength = ByteUtil.serializeVariableLengthInteger((long) userAgentBytes.length);
            byteData.userAgent = new byte[userAgentBytesEncodedLength.length + userAgentBytes.length];
            ByteUtil.setBytes(byteData.userAgent, userAgentBytesEncodedLength);
            ByteUtil.setBytes(byteData.userAgent, userAgentBytes, userAgentBytesEncodedLength.length);
        }

        ByteUtil.setBytes(byteData.currentBlockHeight, ByteUtil.integerToBytes(_currentBlockHeight));

        { // Construct Should-Relay bytes...
            final String hexString = (_relayIsEnabled ? "01" : "00");
            final byte[] newBytesValue = BitcoinUtil.hexStringToByteArray(hexString);
            ByteUtil.setBytes(byteData.shouldRelay, newBytesValue);
        }

        return byteData;
    }

    public SynchronizeVersionMessage() {
        super(Command.SYNCHRONIZE_VERSION);
        _version = VERSION;

        _nodeFeatures.enableFeatureFlag(NodeFeatures.Flags.BLOCKCHAIN_ENABLED);
        _nodeFeatures.enableFeatureFlag(NodeFeatures.Flags.BITCOIN_CASH_ENABLED);

        _remoteNetworkAddress = new NetworkAddress();
        _localNetworkAddress = new NetworkAddress();

        _timestamp = (System.currentTimeMillis() / 1000L);
        _nonce = (long) (Math.random() * Long.MAX_VALUE);
        _currentBlockHeight = 0;

        _userAgent = Constants.USER_AGENT;
    }

    public Integer getVersion() { return _version; }
    public String getUserAgent() { return _userAgent; }
    public NodeFeatures getNodeFeatures() { return _nodeFeatures; }
    public Long getTimestamp() { return _timestamp; }
    public Long getNonce() { return _nonce; }
    public Boolean relayIsEnabled() { return _relayIsEnabled; }
    public Integer getCurrentBlockHeight() { return _currentBlockHeight; }

    public void setNodeFeatures(final NodeFeatures nodeFeatures) {
        _nodeFeatures.setFeaturesFlags(nodeFeatures);
    }

    public void setLocalAddress(final NetworkAddress networkAddress) {
        _localNetworkAddress = networkAddress.duplicate();
    }

    public NetworkAddress getLocalNetworkAddress() {
        return _localNetworkAddress.duplicate();
    }

    public void setRemoteAddress(final NetworkAddress networkAddress) {
        _remoteNetworkAddress = networkAddress.duplicate();
    }

    public NetworkAddress getRemoteNetworkAddress() {
        return _remoteNetworkAddress;
    }

    public void setCurrentBlockHeight(final Integer currentBlockHeight) {
        _currentBlockHeight = currentBlockHeight;
    }

    public void setRelayIsEnabled(final Boolean isEnabled) {
        _relayIsEnabled = isEnabled;
    }

    @Override
    protected byte[] _getPayload() {
        final ByteData byteData = _createByteData();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(byteData.version, Endian.LITTLE);
        byteArrayBuilder.appendBytes(byteData.nodeFeatures, Endian.LITTLE);
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
