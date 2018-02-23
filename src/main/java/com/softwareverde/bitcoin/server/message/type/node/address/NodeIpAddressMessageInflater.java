package com.softwareverde.bitcoin.server.message.type.node.address;

import com.softwareverde.bitcoin.server.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.message.ProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.ProtocolMessageHeader;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class NodeIpAddressMessageInflater extends ProtocolMessageInflater {

    @Override
    public NodeIpAddressMessage fromBytes(final byte[] bytes) {
        final NodeIpAddressInflater nodeIpAddressInflater = new NodeIpAddressInflater();
        final int networkAddressByteCount = NodeIpAddress.BYTE_COUNT_WITH_TIMESTAMP;

        final NodeIpAddressMessage nodeIpAddressMessage = new NodeIpAddressMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final ProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, ProtocolMessage.MessageType.NODE_ADDRESSES);
        if (protocolMessageHeader == null) { return null; }

        final int networkAddressCount = byteArrayReader.readVariableSizedInteger().intValue();
        if (byteArrayReader.remainingByteCount() < (networkAddressCount * networkAddressByteCount)) { return null; }

        for (int i=0; i<networkAddressCount; ++i) {
            final byte[] networkAddressBytes = byteArrayReader.readBytes(networkAddressByteCount, Endian.BIG);
            final NodeIpAddress nodeIpAddress = nodeIpAddressInflater.fromBytes(networkAddressBytes);
            nodeIpAddressMessage._nodeIpAddresses.add(nodeIpAddress);
        }

        if (byteArrayReader.wentOutOfBounds()) { return null; }

        return nodeIpAddressMessage;
    }
}
