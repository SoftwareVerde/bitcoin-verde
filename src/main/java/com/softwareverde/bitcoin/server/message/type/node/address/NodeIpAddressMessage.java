package com.softwareverde.bitcoin.server.message.type.node.address;

import com.softwareverde.bitcoin.server.message.ProtocolMessage;
import com.softwareverde.bitcoin.type.bytearray.ByteArray;
import com.softwareverde.bitcoin.type.bytearray.MutableByteArray;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.util.Util;

import java.util.ArrayList;
import java.util.List;

public class NodeIpAddressMessage extends ProtocolMessage {
    protected final List<NodeIpAddress> _nodeIpAddresses = new ArrayList<NodeIpAddress>();

    public NodeIpAddressMessage() {
        super(MessageType.NODE_ADDRESSES);
    }

    public void addAddress(final NodeIpAddress nodeIpAddress) {
        _nodeIpAddresses.add(nodeIpAddress.copy());
    }

    public List<NodeIpAddress> getNodeIpAddresses() {
        return Util.copyList(_nodeIpAddresses);
    }

    @Override
    protected ByteArray _getPayload() {
        final int networkAddressByteCount = NodeIpAddress.BYTE_COUNT_WITH_TIMESTAMP;
        final int networkAddressCount = _nodeIpAddresses.size();

        final byte[] addressCountBytes = ByteUtil.variableLengthIntegerToBytes(networkAddressCount);
        final byte[] networkAddressesBytes = new byte[networkAddressCount * networkAddressByteCount];

        int addressesByteCount = 0;
        final List<byte[]> addressesBytes = new ArrayList<byte[]>(networkAddressCount);
        for (final NodeIpAddress nodeIpAddress : _nodeIpAddresses) {
            final byte[] networkAddressBytes = nodeIpAddress.getBytesWithTimestamp();
            addressesBytes.add(networkAddressBytes);
            addressesByteCount += networkAddressBytes.length;
        }

        for (int i=0; i<networkAddressCount; ++i) {
            final byte[] networkAddressBytes = addressesBytes.get(i);
            final int writeIndex = (networkAddressByteCount * i);
            ByteUtil.setBytes(networkAddressesBytes, networkAddressBytes, writeIndex);
        }

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(addressCountBytes, Endian.BIG);
        byteArrayBuilder.appendBytes(networkAddressesBytes, Endian.BIG);
        return new MutableByteArray(byteArrayBuilder.build());
    }
}
