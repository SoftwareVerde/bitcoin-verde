package com.softwareverde.bitcoin.server.message.type.query.response.hash;

import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class DataHashInflater {
    public static final Integer HASH_BYTE_COUNT = 32;

    public DataHash fromBytes(final ByteArrayReader byteArrayReader) {
        final Integer inventoryTypeCode = byteArrayReader.readInteger(4, Endian.LITTLE);
        final byte[] objectHash = byteArrayReader.readBytes(HASH_BYTE_COUNT, Endian.LITTLE);

        final DataHashType dataType = DataHashType.fromValue(inventoryTypeCode);
        final DataHash dataHash = new DataHash(dataType, objectHash);
        return dataHash;
    }
}
