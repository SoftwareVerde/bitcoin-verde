package com.softwareverde.bitcoin.server.message.type.node.address;

import com.softwareverde.bitcoin.inflater.ProtocolMessageInflaters;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class NodeIpAddressMessageInflater extends BitcoinProtocolMessageInflater {
    protected final NodeIpAddressInflater _nodeIpAddressInflater;

    public NodeIpAddressMessageInflater(final ProtocolMessageInflaters protocolMessageInflaters) {
        _nodeIpAddressInflater = protocolMessageInflaters.getNodeIpAddressInflater();
    }

    @Override
    public BitcoinNodeIpAddressMessage fromBytes(final byte[] bytes) {
        final int networkAddressByteCount = BitcoinNodeIpAddress.BYTE_COUNT_WITH_TIMESTAMP;

        final BitcoinNodeIpAddressMessage nodeIpAddressMessage = new BitcoinNodeIpAddressMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.NODE_ADDRESSES);
        if (protocolMessageHeader == null) { return null; }

        final CompactVariableLengthInteger networkAddressCount = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader);
        if (! networkAddressCount.isCanonical()) { return null; }
        if (byteArrayReader.remainingByteCount() < (networkAddressCount.intValue() * networkAddressByteCount)) { return null; }

        for (int i = 0; i < networkAddressCount.value; ++i) {
            final byte[] networkAddressBytes = byteArrayReader.readBytes(networkAddressByteCount, Endian.BIG);
            final BitcoinNodeIpAddress nodeIpAddress = _nodeIpAddressInflater.fromBytes(networkAddressBytes);
            nodeIpAddressMessage._nodeIpAddresses.add(nodeIpAddress);
        }

        if (byteArrayReader.didOverflow()) { return null; }

        return nodeIpAddressMessage;
    }
}
