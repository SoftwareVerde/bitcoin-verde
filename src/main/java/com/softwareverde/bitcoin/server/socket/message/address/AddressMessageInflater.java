package com.softwareverde.bitcoin.server.socket.message.address;

import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageHeader;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageInflater;
import com.softwareverde.bitcoin.server.socket.message.networkaddress.NetworkAddress;
import com.softwareverde.bitcoin.server.socket.message.networkaddress.NetworkAddressInflater;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class AddressMessageInflater extends ProtocolMessageInflater {

    @Override
    public AddressMessage fromBytes(final byte[] bytes) {
        final NetworkAddressInflater networkAddressInflater = new NetworkAddressInflater();
        final int networkAddressByteCount = NetworkAddress.BYTE_COUNT_WITH_TIMESTAMP;

        final AddressMessage addressMessage = new AddressMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final ProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, ProtocolMessage.Command.ADDRESS);
        if (protocolMessageHeader == null) { return null; }

        final int networkAddressCount = byteArrayReader.readVariableSizedInteger().intValue();
        if (byteArrayReader.remainingByteCount() < (networkAddressCount * networkAddressByteCount)) { return null; }

        for (int i=0; i<networkAddressCount; ++i) {
            final byte[] networkAddressBytes = byteArrayReader.readBytes(networkAddressByteCount, Endian.BIG);
            final NetworkAddress networkAddress = networkAddressInflater.fromBytes(networkAddressBytes);
            addressMessage._networkAddresses.add(networkAddress);
        }

        return addressMessage;
    }
}
