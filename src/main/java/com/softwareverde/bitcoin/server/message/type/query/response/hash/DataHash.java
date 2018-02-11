package com.softwareverde.bitcoin.server.message.type.query.response.hash;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class DataHash {
    private final DataHashType _dataHashType;
    private byte[] _objectHash = new byte[32];

    public DataHash(final DataHashType dataHashType, final byte[] objectHash) {
        _dataHashType = dataHashType;
        ByteUtil.setBytes(_objectHash, objectHash);
    }

    public DataHashType getDataHashType() {
        return _dataHashType;
    }

    public byte[] getObjectHash() {
        return _objectHash;
    }

    public byte[] getBytes() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        final byte[] inventoryTypeBytes = new byte[4];
        ByteUtil.setBytes(inventoryTypeBytes, ByteUtil.integerToBytes(_dataHashType.getValue()));

        byteArrayBuilder.appendBytes(inventoryTypeBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(_objectHash, Endian.LITTLE);

        return byteArrayBuilder.build();
    }
}