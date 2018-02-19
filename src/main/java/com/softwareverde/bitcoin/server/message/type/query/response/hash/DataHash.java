package com.softwareverde.bitcoin.server.message.type.query.response.hash;

import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class DataHash {
    private final DataHashType _dataHashType;
    private final ImmutableHash _objectHash;

    public DataHash(final DataHashType dataHashType, final ImmutableHash objectHash) {
        _dataHashType = dataHashType;
        _objectHash = objectHash;
    }

    public DataHashType getDataHashType() {
        return _dataHashType;
    }

    public ImmutableHash getObjectHash() {
        return _objectHash;
    }

    public byte[] getBytes() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        final byte[] inventoryTypeBytes = new byte[4];
        ByteUtil.setBytes(inventoryTypeBytes, ByteUtil.integerToBytes(_dataHashType.getValue()));

        byteArrayBuilder.appendBytes(inventoryTypeBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(_objectHash.getBytes(), Endian.LITTLE);

        return byteArrayBuilder.build();
    }
}