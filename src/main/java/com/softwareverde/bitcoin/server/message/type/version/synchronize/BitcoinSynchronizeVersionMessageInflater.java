package com.softwareverde.bitcoin.server.message.type.version.synchronize;

import com.softwareverde.bitcoin.inflater.ProtocolMessageInflaters;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.node.address.NodeIpAddressInflater;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class BitcoinSynchronizeVersionMessageInflater extends BitcoinProtocolMessageInflater {
    protected final NodeIpAddressInflater _nodeIpAddressInflater;

    public BitcoinSynchronizeVersionMessageInflater(final ProtocolMessageInflaters protocolMessageInflaters) {
        _nodeIpAddressInflater = protocolMessageInflaters.getNodeIpAddressInflater();
    }

    @Override
    public BitcoinSynchronizeVersionMessage fromBytes(final byte[] bytes) {
        final BitcoinSynchronizeVersionMessage synchronizeVersionMessage = new BitcoinSynchronizeVersionMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.SYNCHRONIZE_VERSION);
        if (protocolMessageHeader == null) { return null; }

        synchronizeVersionMessage._version = byteArrayReader.readInteger(4, Endian.LITTLE);

        final Long nodeFeatureFlags = byteArrayReader.readLong(8, Endian.LITTLE);
        synchronizeVersionMessage._nodeFeatures.setFeatureFlags(nodeFeatureFlags);

        synchronizeVersionMessage._timestampInSeconds = byteArrayReader.readLong(8, Endian.LITTLE);

        final byte[] remoteNetworkAddressBytes = byteArrayReader.readBytes(26, Endian.BIG);
        synchronizeVersionMessage._remoteNodeIpAddress = _nodeIpAddressInflater.fromBytes(remoteNetworkAddressBytes);

        final byte[] localNetworkAddressBytes = byteArrayReader.readBytes(26, Endian.BIG);
        synchronizeVersionMessage._localNodeIpAddress = _nodeIpAddressInflater.fromBytes(localNetworkAddressBytes);

        synchronizeVersionMessage._nonce = byteArrayReader.readLong(8, Endian.LITTLE);
        synchronizeVersionMessage._userAgent = CompactVariableLengthInteger.readVariableLengthString(byteArrayReader);
        synchronizeVersionMessage._currentBlockHeight = byteArrayReader.readLong(4, Endian.LITTLE);
        synchronizeVersionMessage._transactionRelayIsEnabled = byteArrayReader.readBoolean();

        if (byteArrayReader.didOverflow()) { return null; }

        return synchronizeVersionMessage;
    }
}
