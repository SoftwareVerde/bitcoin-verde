package com.softwareverde.bitcoin.server.message.type.node.address;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class NodeIpAddressMessageInflater extends BitcoinProtocolMessageInflater {

    @Override
    public NodeIpAddressMessage fromBytes(final byte[] bytes) {
        final NodeIpAddressInflater nodeIpAddressInflater = new NodeIpAddressInflater();
        final int networkAddressByteCount = BitcoinNodeIpAddress.BYTE_COUNT_WITH_TIMESTAMP;

        final NodeIpAddressMessage nodeIpAddressMessage = new NodeIpAddressMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.NODE_ADDRESSES);
        if (protocolMessageHeader == null) { return null; }

        final int networkAddressCount = byteArrayReader.readVariableSizedInteger().intValue();
        if (byteArrayReader.remainingByteCount() < (networkAddressCount * networkAddressByteCount)) { return null; }

        for (int i=0; i<networkAddressCount; ++i) {
            final byte[] networkAddressBytes = byteArrayReader.readBytes(networkAddressByteCount, Endian.BIG);
            final BitcoinNodeIpAddress nodeIpAddress = nodeIpAddressInflater.fromBytes(networkAddressBytes);
            nodeIpAddressMessage._nodeIpAddresses.add(nodeIpAddress);
        }

        if (byteArrayReader.didOverflow()) { return null; }

        return nodeIpAddressMessage;
    }
}
