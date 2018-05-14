package com.softwareverde.bitcoin.server.message.type.version.synchronize;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.node.address.NodeIpAddressInflater;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class BitcoinSynchronizeVersionMessageInflater extends BitcoinProtocolMessageInflater {

    @Override
    public BitcoinSynchronizeVersionMessage fromBytes(final byte[] bytes) {
        final NodeIpAddressInflater nodeIpAddressInflater = new NodeIpAddressInflater();
        final BitcoinSynchronizeVersionMessage synchronizeVersionMessage = new BitcoinSynchronizeVersionMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.SYNCHRONIZE_VERSION);
        if (protocolMessageHeader == null) { return null; }

        synchronizeVersionMessage._version = byteArrayReader.readInteger(4, Endian.LITTLE);

        final Long nodeFeatureFlags = byteArrayReader.readLong(8, Endian.LITTLE);
        synchronizeVersionMessage._nodeFeatures.setFeatureFlags(nodeFeatureFlags);

        synchronizeVersionMessage._timestampInSeconds = byteArrayReader.readLong(8, Endian.LITTLE);

        final byte[] remoteNetworkAddressBytes = byteArrayReader.readBytes(26, Endian.BIG);
        synchronizeVersionMessage._remoteNodeIpAddress = nodeIpAddressInflater.fromBytes(remoteNetworkAddressBytes);

        final byte[] localNetworkAddressBytes = byteArrayReader.readBytes(26, Endian.BIG);
        synchronizeVersionMessage._localNodeIpAddress = nodeIpAddressInflater.fromBytes(localNetworkAddressBytes);

        synchronizeVersionMessage._nonce = byteArrayReader.readLong(8, Endian.LITTLE);
        synchronizeVersionMessage._userAgent = byteArrayReader.readVariableLengthString();
        synchronizeVersionMessage._currentBlockHeight = byteArrayReader.readInteger(4, Endian.LITTLE);
        synchronizeVersionMessage._relayIsEnabled = byteArrayReader.readBoolean();

        if (byteArrayReader.didOverflow()) { return null; }

        return synchronizeVersionMessage;
    }
}
