package com.softwareverde.bitcoin.server.message.type.node.address;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.util.ConstUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.p2p.message.type.NodeIpAddressMessage;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class BitcoinNodeIpAddressMessage extends BitcoinProtocolMessage implements NodeIpAddressMessage {
    protected final MutableList<BitcoinNodeIpAddress> _nodeIpAddresses = new MutableArrayList<>();

    public BitcoinNodeIpAddressMessage() {
        super(MessageType.NODE_ADDRESSES);
    }

    @Override
    public void addAddress(final NodeIpAddress nodeIpAddress) {
        if (! (nodeIpAddress instanceof BitcoinNodeIpAddress)) {
            Logger.warn("Invalid NodeIpAddress type provided to BitcoinNodeIpAddressMessage.");
            return;
        }

        _nodeIpAddresses.add((BitcoinNodeIpAddress) nodeIpAddress);
    }

    @Override
    public List<NodeIpAddress> getNodeIpAddresses() {
        return ConstUtil.downcastList(_nodeIpAddresses);
    }

    @Override
    protected ByteArray _getPayload() {
        final int networkAddressByteCount = BitcoinNodeIpAddress.BYTE_COUNT_WITH_TIMESTAMP;
        final int networkAddressCount = _nodeIpAddresses.getCount();

        final ByteArray addressCountBytes = CompactVariableLengthInteger.variableLengthIntegerToBytes(networkAddressCount);
        final byte[] networkAddressesBytes = new byte[networkAddressCount * networkAddressByteCount];

        int addressesByteCount = 0;
        final MutableList<byte[]> addressesBytes = new MutableArrayList<>(networkAddressCount);
        for (final BitcoinNodeIpAddress nodeIpAddress : _nodeIpAddresses) {
            final byte[] networkAddressBytes = nodeIpAddress.getBytesWithTimestamp();
            addressesBytes.add(networkAddressBytes);
            addressesByteCount += networkAddressBytes.length;
        }

        for (int i = 0; i < networkAddressCount; ++i) {
            final byte[] networkAddressBytes = addressesBytes.get(i);
            final int writeIndex = (networkAddressByteCount * i);
            ByteUtil.setBytes(networkAddressesBytes, networkAddressBytes, writeIndex);
        }

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(addressCountBytes, Endian.BIG);
        byteArrayBuilder.appendBytes(networkAddressesBytes, Endian.BIG);
        return byteArrayBuilder;
    }

    @Override
    protected Integer _getPayloadByteCount() {
        final int networkAddressByteCount = BitcoinNodeIpAddress.BYTE_COUNT_WITH_TIMESTAMP;
        final int networkAddressCount = _nodeIpAddresses.getCount();

        final ByteArray addressCountBytes = CompactVariableLengthInteger.variableLengthIntegerToBytes(networkAddressCount);
        return (addressCountBytes.getByteCount() + (networkAddressCount * networkAddressByteCount));
    }
}
