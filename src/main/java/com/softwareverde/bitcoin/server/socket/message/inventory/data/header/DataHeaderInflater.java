package com.softwareverde.bitcoin.server.socket.message.inventory.data.header;

import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class DataHeaderInflater {
    public static final Integer HASH_BYTE_COUNT = 32;

    public DataHeader fromBytes(final ByteArrayReader byteArrayReader) {
        final Integer inventoryTypeCode = byteArrayReader.readInteger(4, Endian.LITTLE);
        final byte[] objectHash = byteArrayReader.readBytes(HASH_BYTE_COUNT, Endian.LITTLE);

        final DataHeaderType dataType = DataHeaderType.fromValue(inventoryTypeCode);
        final DataHeader dataHeader = new DataHeader(dataType, objectHash);
        return dataHeader;
    }
}
