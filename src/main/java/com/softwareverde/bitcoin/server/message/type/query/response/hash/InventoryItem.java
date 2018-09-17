package com.softwareverde.bitcoin.server.message.type.query.response.hash;

import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class InventoryItem {
    private final InventoryItemType _inventoryItemType;
    private final Sha256Hash _objectHash;

    public InventoryItem(final InventoryItemType inventoryItemType, final Sha256Hash objectHash) {
        _inventoryItemType = inventoryItemType;

        if (objectHash instanceof ImmutableSha256Hash) {
            _objectHash = objectHash;
        }
        else {
            _objectHash = new ImmutableSha256Hash(objectHash);
        }
    }

    public InventoryItemType getItemType() {
        return _inventoryItemType;
    }

    public Sha256Hash getItemHash() {
        return _objectHash;
    }

    public byte[] getBytes() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        final byte[] inventoryTypeBytes = new byte[4];
        ByteUtil.setBytes(inventoryTypeBytes, ByteUtil.integerToBytes(_inventoryItemType.getValue()));

        byteArrayBuilder.appendBytes(inventoryTypeBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(_objectHash.getBytes(), Endian.LITTLE);

        return byteArrayBuilder.build();
    }

    @Override
    public String toString() {
        return (_inventoryItemType + ":" + _objectHash);
    }
}