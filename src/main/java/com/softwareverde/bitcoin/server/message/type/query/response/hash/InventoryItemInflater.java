package com.softwareverde.bitcoin.server.message.type.query.response.hash;

import com.softwareverde.cryptography.hash.sha256.MutableSha256Hash;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class InventoryItemInflater {
    public static final Integer BYTE_COUNT = (4 + Sha256Hash.BYTE_COUNT);

    public InventoryItem fromBytes(final ByteArrayReader byteArrayReader) {
        final Integer inventoryTypeCode = byteArrayReader.readInteger(4, Endian.LITTLE);
        final Sha256Hash objectHash = MutableSha256Hash.wrap(byteArrayReader.readBytes(Sha256Hash.BYTE_COUNT, Endian.LITTLE));

        if (byteArrayReader.didOverflow()) { return null; }

        final InventoryItemType dataType = InventoryItemType.fromValue(inventoryTypeCode);
        if (dataType == null) { return null; }

        return new InventoryItem(dataType, objectHash);
    }
}
