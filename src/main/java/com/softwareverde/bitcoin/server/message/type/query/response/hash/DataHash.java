package com.softwareverde.bitcoin.server.message.type.query.response.hash;

import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class DataHash {
    private final DataHashType _dataHashType;
    private final Sha256Hash _objectHash;

    public DataHash(final DataHashType dataHashType, final Sha256Hash objectHash) {
        _dataHashType = dataHashType;

        if (objectHash instanceof ImmutableSha256Hash) {
            _objectHash = objectHash;
        }
        else {
            _objectHash = new ImmutableSha256Hash(objectHash);
        }
    }

    public DataHashType getDataHashType() {
        return _dataHashType;
    }

    public Sha256Hash getObjectHash() {
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