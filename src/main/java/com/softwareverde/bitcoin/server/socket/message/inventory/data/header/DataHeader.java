package com.softwareverde.bitcoin.server.socket.message.inventory.data.header;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class DataHeader {
    private final DataHeaderType _dataHeaderType;
    private byte[] _objectHash = new byte[32];

    public DataHeader(final DataHeaderType dataHeaderType, final byte[] objectHash) {
        _dataHeaderType = dataHeaderType;
        ByteUtil.setBytes(_objectHash, objectHash);
    }

    public DataHeaderType getDataHeaderType() {
        return _dataHeaderType;
    }

    public byte[] getObjectHash() {
        return _objectHash;
    }

    public byte[] getBytes() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        final byte[] inventoryTypeBytes = new byte[4];
        ByteUtil.setBytes(inventoryTypeBytes, ByteUtil.integerToBytes(_dataHeaderType.getValue()));

        byteArrayBuilder.appendBytes(inventoryTypeBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(_objectHash, Endian.LITTLE);

        return byteArrayBuilder.build();
    }
}