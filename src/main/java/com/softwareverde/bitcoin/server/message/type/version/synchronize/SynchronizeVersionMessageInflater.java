package com.softwareverde.bitcoin.server.message.type.version.synchronize;

import com.softwareverde.bitcoin.server.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.message.ProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.ProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.node.address.NodeIpAddressInflater;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class SynchronizeVersionMessageInflater extends ProtocolMessageInflater {

    @Override
    public SynchronizeVersionMessage fromBytes(final byte[] bytes) {
        final NodeIpAddressInflater nodeIpAddressInflater = new NodeIpAddressInflater();
        final SynchronizeVersionMessage synchronizeVersionMessage = new SynchronizeVersionMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final ProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, ProtocolMessage.MessageType.SYNCHRONIZE_VERSION);
        if (protocolMessageHeader == null) { return null; }

        synchronizeVersionMessage._version = byteArrayReader.readInteger(4, Endian.LITTLE);

        final Long nodeFeatureFlags = byteArrayReader.readLong(8, Endian.LITTLE);
        synchronizeVersionMessage._nodeFeatures.setFeatureFlags(nodeFeatureFlags);

        synchronizeVersionMessage._timestamp = byteArrayReader.readLong(8, Endian.LITTLE);

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
