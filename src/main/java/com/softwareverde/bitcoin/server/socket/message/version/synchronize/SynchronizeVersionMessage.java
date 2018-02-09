package com.softwareverde.bitcoin.server.socket.message.version.synchronize;

import com.softwareverde.bitcoin.server.Constants;
import com.softwareverde.bitcoin.server.socket.message.NodeFeatures;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.socket.message.networkaddress.NetworkAddress;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class SynchronizeVersionMessage extends ProtocolMessage {
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

    protected Integer _version;
    protected String _userAgent;
    protected final NodeFeatures _nodeFeatures = new NodeFeatures();
    protected Long _timestamp;
    protected NetworkAddress _remoteNetworkAddress;
    protected NetworkAddress _localNetworkAddress;
    protected Long _nonce;
    protected Integer _currentBlockHeight;
    protected Boolean _relayIsEnabled = false;

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