package com.softwareverde.bitcoin.server.message.type.version.synchronize;

import com.softwareverde.bitcoin.server.Constants;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.network.p2p.message.type.SynchronizeVersionMessage;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

public class BitcoinSynchronizeVersionMessage extends BitcoinProtocolMessage implements SynchronizeVersionMessage<MessageType> {
    protected Integer _version;
    protected String _userAgent;
    protected final NodeFeatures _nodeFeatures = new NodeFeatures();
    protected Long _timestamp;
    protected BitcoinNodeIpAddress _remoteNodeIpAddress;
    protected BitcoinNodeIpAddress _localNodeIpAddress;
    protected Long _nonce;
    protected Integer _currentBlockHeight;
    protected Boolean _relayIsEnabled = false;

    public BitcoinSynchronizeVersionMessage() {
        super(MessageType.SYNCHRONIZE_VERSION);
        _version = Constants.PROTOCOL_VERSION;

        _nodeFeatures.enableFeatureFlag(NodeFeatures.Flags.BLOCKCHAIN_ENABLED);
        _nodeFeatures.enableFeatureFlag(NodeFeatures.Flags.BITCOIN_CASH_ENABLED);

        _remoteNodeIpAddress = new BitcoinNodeIpAddress();
        _localNodeIpAddress = new BitcoinNodeIpAddress();

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

    public void setLocalAddress(final BitcoinNodeIpAddress nodeIpAddress) {
        _localNodeIpAddress = nodeIpAddress.copy();
    }

    public BitcoinNodeIpAddress getLocalNodeIpAddress() {
        return _localNodeIpAddress.copy();
    }

    public void setRemoteAddress(final BitcoinNodeIpAddress nodeIpAddress) {
        _remoteNodeIpAddress = nodeIpAddress.copy();
    }

    public BitcoinNodeIpAddress getRemoteNodeIpAddress() {
        return _remoteNodeIpAddress;
    }

    public void setCurrentBlockHeight(final Integer currentBlockHeight) {
        _currentBlockHeight = currentBlockHeight;
    }

    public void setRelayIsEnabled(final Boolean isEnabled) {
        _relayIsEnabled = isEnabled;
    }

    @Override
    protected ByteArray _getPayload() {
        final byte[] version            = new byte[4];
        final byte[] nodeFeatures       = new byte[8];
        final byte[] timestamp          = new byte[8];
        final byte[] remoteAddress      = new byte[26];
        final byte[] localAddress       = new byte[26];
        final byte[] nonce              = new byte[8];
        final byte[] userAgent;
        final byte[] currentBlockHeight = new byte[4];
        final byte[] shouldRelay        = new byte[1];

        ByteUtil.setBytes(version, ByteUtil.integerToBytes(_version));
        ByteUtil.setBytes(nodeFeatures, ByteUtil.longToBytes(_nodeFeatures.getFeatureFlags()));
        ByteUtil.setBytes(timestamp, ByteUtil.longToBytes(_timestamp));
        ByteUtil.setBytes(remoteAddress, _remoteNodeIpAddress.getBytesWithoutTimestamp());
        ByteUtil.setBytes(localAddress, _localNodeIpAddress.getBytesWithoutTimestamp());
        ByteUtil.setBytes(nonce, ByteUtil.longToBytes(_nonce));

        { // Construct User-Agent bytes...
            final byte[] userAgentBytes = _userAgent.getBytes();
            final byte[] userAgentBytesEncodedLength = ByteUtil.variableLengthIntegerToBytes((long) userAgentBytes.length);
            userAgent = new byte[userAgentBytesEncodedLength.length + userAgentBytes.length];
            ByteUtil.setBytes(userAgent, userAgentBytesEncodedLength);
            ByteUtil.setBytes(userAgent, userAgentBytes, userAgentBytesEncodedLength.length);
        }

        ByteUtil.setBytes(currentBlockHeight, ByteUtil.integerToBytes(_currentBlockHeight));

        shouldRelay[0] = (byte) (_relayIsEnabled ? 0x01 : 0x00);

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(version, Endian.LITTLE);
        byteArrayBuilder.appendBytes(nodeFeatures, Endian.LITTLE);
        byteArrayBuilder.appendBytes(timestamp, Endian.LITTLE);
        byteArrayBuilder.appendBytes(remoteAddress, Endian.BIG);
        byteArrayBuilder.appendBytes(localAddress, Endian.BIG);
        byteArrayBuilder.appendBytes(nonce, Endian.LITTLE);
        byteArrayBuilder.appendBytes(userAgent, Endian.BIG);
        byteArrayBuilder.appendBytes(currentBlockHeight, Endian.LITTLE);
        byteArrayBuilder.appendBytes(shouldRelay, Endian.LITTLE);
        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}
