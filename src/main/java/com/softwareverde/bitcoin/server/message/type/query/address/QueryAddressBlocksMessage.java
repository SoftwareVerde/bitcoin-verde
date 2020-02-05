package com.softwareverde.bitcoin.server.message.type.query.address;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

// BitcoinVerde 2019-04-22
public class QueryAddressBlocksMessage extends BitcoinProtocolMessage {
    public static final Integer MAX_ADDRESS_COUNT = 1024;

    protected final MutableList<Address> _addresses = new MutableList<Address>();

    public QueryAddressBlocksMessage() {
        super(MessageType.QUERY_ADDRESS_BLOCKS);
    }

    public void addAddress(final Address address) {
        if (_addresses.getCount() >= MAX_ADDRESS_COUNT) { return; }
        _addresses.add(address);
    }

    public List<Address> getAddresses() {
        return _addresses;
    }

    @Override
    protected ByteArray _getPayload() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(_addresses.getCount()));
        for (final Address address : _addresses) {
            byteArrayBuilder.appendBytes(address.getBytes(), Endian.LITTLE);
        }

        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}
