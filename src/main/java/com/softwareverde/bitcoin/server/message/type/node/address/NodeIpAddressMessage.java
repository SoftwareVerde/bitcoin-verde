package com.softwareverde.bitcoin.server.message.type.node.address;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

import java.util.ArrayList;
import java.util.List;

public class NodeIpAddressMessage extends BitcoinProtocolMessage {
    protected final List<BitcoinNodeIpAddress> _nodeIpAddresses = new ArrayList<BitcoinNodeIpAddress>();

    public NodeIpAddressMessage() {
        super(MessageType.NODE_ADDRESSES);
    }

    public void addAddress(final BitcoinNodeIpAddress nodeIpAddress) {
        _nodeIpAddresses.add(nodeIpAddress.copy());
    }

    public List<BitcoinNodeIpAddress> getNodeIpAddresses() {
        return Util.copyList(_nodeIpAddresses);
    }

    @Override
    protected ByteArray _getPayload() {
        final int networkAddressByteCount = BitcoinNodeIpAddress.BYTE_COUNT_WITH_TIMESTAMP;
        final int networkAddressCount = _nodeIpAddresses.size();

        final byte[] addressCountBytes = ByteUtil.variableLengthIntegerToBytes(networkAddressCount);
        final byte[] networkAddressesBytes = new byte[networkAddressCount * networkAddressByteCount];

        int addressesByteCount = 0;
        final List<byte[]> addressesBytes = new ArrayList<byte[]>(networkAddressCount);
        for (final BitcoinNodeIpAddress nodeIpAddress : _nodeIpAddresses) {
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
        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}
