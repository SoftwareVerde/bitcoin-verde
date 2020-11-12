package com.softwareverde.bitcoin.server.message.type.version.synchronize;

import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.network.p2p.message.type.SynchronizeVersionMessage;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class BitcoinSynchronizeVersionMessage extends BitcoinProtocolMessage implements SynchronizeVersionMessage {
    protected static class Struct {
        final byte[] version                    = new byte[4];
        final byte[] nodeFeatures               = new byte[8];
        final byte[] timestamp                  = new byte[8];
        final byte[] remoteAddress              = new byte[26];
        final byte[] localAddress               = new byte[26];
        final byte[] nonce                      = new byte[8];
        final byte[] userAgent;
        final byte[] currentBlockHeight         = new byte[4];
        final byte[] shouldRelayTransactions    = new byte[1];

        public Struct(final int userAgentByteCount) {
            this.userAgent = new byte[userAgentByteCount];
        }
    }

    protected Integer _version;
    protected String _userAgent;
    protected final NodeFeatures _nodeFeatures = new NodeFeatures();
    protected Long _timestampInSeconds;
    protected BitcoinNodeIpAddress _remoteNodeIpAddress;
    protected BitcoinNodeIpAddress _localNodeIpAddress;
    protected Long _nonce;
    protected Long _currentBlockHeight;
    protected Boolean _transactionRelayIsEnabled = false;

    protected byte[] _getUserAgentBytes() {
        final byte[] userAgentBytes = _userAgent.getBytes();
        final byte[] userAgentBytesEncodedLength = ByteUtil.variableLengthIntegerToBytes(userAgentBytes.length);

        final byte[] bytes = new byte[userAgentBytesEncodedLength.length + userAgentBytes.length];
        ByteUtil.setBytes(bytes, userAgentBytesEncodedLength);
        ByteUtil.setBytes(bytes, userAgentBytes, userAgentBytesEncodedLength.length);
        return bytes;
    }

    public BitcoinSynchronizeVersionMessage() {
        super(MessageType.SYNCHRONIZE_VERSION);
        _version = BitcoinConstants.getProtocolVersion();

        _remoteNodeIpAddress = new BitcoinNodeIpAddress();
        _localNodeIpAddress = new BitcoinNodeIpAddress();

        _timestampInSeconds = (System.currentTimeMillis() / 1000L);
        _nonce = (long) (Math.random() * Long.MAX_VALUE);
        _currentBlockHeight = 0L;

        _userAgent = BitcoinConstants.getUserAgent();
    }

    public Integer getVersion() { return _version; }

    @Override
    public String getUserAgent() { return _userAgent; }

    public NodeFeatures getNodeFeatures() { return _nodeFeatures; }

    public Boolean transactionRelayIsEnabled() { return _transactionRelayIsEnabled; }

    public Long getCurrentBlockHeight() { return _currentBlockHeight; }

    public void setNodeFeatures(final NodeFeatures nodeFeatures) {
        _nodeFeatures.setFeaturesFlags(nodeFeatures);
    }

    public void setLocalAddress(final BitcoinNodeIpAddress nodeIpAddress) {
        _localNodeIpAddress = nodeIpAddress.copy();
    }

    @Override
    public Long getNonce() { return _nonce; }

    @Override
    public Long getTimestamp() { return _timestampInSeconds; }

    @Override
    public BitcoinNodeIpAddress getLocalNodeIpAddress() {
        return _localNodeIpAddress.copy();
    }

    public void setRemoteAddress(final BitcoinNodeIpAddress nodeIpAddress) {
        _remoteNodeIpAddress = nodeIpAddress.copy();
    }

    @Override
    public BitcoinNodeIpAddress getRemoteNodeIpAddress() {
        return _remoteNodeIpAddress;
    }

    public void setCurrentBlockHeight(final Long currentBlockHeight) {
        _currentBlockHeight = currentBlockHeight;
    }

    public void setTransactionRelayIsEnabled(final Boolean isEnabled) {
        _transactionRelayIsEnabled = isEnabled;
    }

    @Override
    protected ByteArray _getPayload() {
        final byte[] userAgentBytes = _getUserAgentBytes();
        final Struct struct = new Struct(userAgentBytes.length);

        ByteUtil.setBytes(struct.version, ByteUtil.integerToBytes(_version));
        ByteUtil.setBytes(struct.nodeFeatures, ByteUtil.longToBytes(_nodeFeatures.getFeatureFlags()));
        ByteUtil.setBytes(struct.timestamp, ByteUtil.longToBytes(_timestampInSeconds));
        ByteUtil.setBytes(struct.remoteAddress, _remoteNodeIpAddress.getBytesWithoutTimestamp());
        ByteUtil.setBytes(struct.localAddress, _localNodeIpAddress.getBytesWithoutTimestamp());
        ByteUtil.setBytes(struct.nonce, ByteUtil.longToBytes(_nonce));
        ByteUtil.setBytes(struct.userAgent, userAgentBytes);
        ByteUtil.setBytes(struct.currentBlockHeight, ByteUtil.integerToBytes(_currentBlockHeight));

        struct.shouldRelayTransactions[0] = (byte) (_transactionRelayIsEnabled ? 0x01 : 0x00);

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(struct.version, Endian.LITTLE);
        byteArrayBuilder.appendBytes(struct.nodeFeatures, Endian.LITTLE);
        byteArrayBuilder.appendBytes(struct.timestamp, Endian.LITTLE);
        byteArrayBuilder.appendBytes(struct.remoteAddress, Endian.BIG);
        byteArrayBuilder.appendBytes(struct.localAddress, Endian.BIG);
        byteArrayBuilder.appendBytes(struct.nonce, Endian.LITTLE);
        byteArrayBuilder.appendBytes(struct.userAgent, Endian.BIG);
        byteArrayBuilder.appendBytes(struct.currentBlockHeight, Endian.LITTLE);
        byteArrayBuilder.appendBytes(struct.shouldRelayTransactions, Endian.LITTLE);
        return byteArrayBuilder;
    }

    @Override
    protected Integer _getPayloadByteCount() {
        final byte[] userAgentBytes = _getUserAgentBytes();
        final Struct struct = new Struct(userAgentBytes.length);

        return (
            struct.version.length +
            struct.nodeFeatures.length +
            struct.timestamp.length +
            struct.remoteAddress.length +
            struct.localAddress.length +
            struct.nonce.length +
            struct.userAgent.length +
            struct.currentBlockHeight.length +
            struct.shouldRelayTransactions.length
        );
    }
}
