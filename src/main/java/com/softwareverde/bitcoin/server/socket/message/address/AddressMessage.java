package com.softwareverde.bitcoin.server.socket.message.address;

import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.socket.message.networkaddress.NetworkAddress;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.util.Util;

import java.util.ArrayList;
import java.util.List;

public class AddressMessage extends ProtocolMessage {
    protected final List<NetworkAddress> _networkAddresses = new ArrayList<NetworkAddress>();

    public AddressMessage() {
        super(MessageType.PEER_ADDRESSES);
    }

    public void addAddress(final NetworkAddress networkAddress) {
        _networkAddresses.add(networkAddress.duplicate());
    }

    public List<NetworkAddress> getNetworkAddresses() {
        return Util.copyList(_networkAddresses);
    }

    @Override
    protected byte[] _getPayload() {
        final int networkAddressByteCount = NetworkAddress.BYTE_COUNT_WITH_TIMESTAMP;
        final int networkAddressCount = _networkAddresses.size();

        final byte[] addressCountBytes = ByteUtil.variableLengthIntegerToBytes(networkAddressCount);
        final byte[] networkAddressesBytes = new byte[networkAddressCount * networkAddressByteCount];

        int addressesByteCount = 0;
        final List<byte[]> addressesBytes = new ArrayList<byte[]>(networkAddressCount);
        for (final NetworkAddress networkAddress : _networkAddresses) {
            final byte[] networkAddressBytes = networkAddress.getBytesWithTimestamp();
            addressesBytes.add(networkAddressBytes);
            addressesByteCount += networkAddressBytes.length;
        }

        for (int i=0; i<networkAddressCount; ++i) {
            final byte[] networkAddressBytes = addressesBytes.get(i);
            final int writeIndex = (networkAddressByteCount * i);
            ByteUtil.setBytes(networkAddressesBytes, networkAddressBytes, writeIndex);
        }

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(addressCountBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(networkAddressesBytes, Endian.BIG);
        return byteArrayBuilder.build();
    }
}
