package com.softwareverde.bitcoin.server.message.type.query;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItemType;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class RequestDataMessage extends BitcoinProtocolMessage {
    public static final Integer MAX_COUNT = 50000;

    public static PublicKey convertUtxoCommitmentInventoryToPublicKey(final InventoryItemType inventoryItemType, final ByteArray inventoryItemPayload) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendByte(inventoryItemType == InventoryItemType.UTXO_COMMITMENT_EVEN ? PublicKey.COMPRESSED_FIRST_BYTE_0 : PublicKey.COMPRESSED_FIRST_BYTE_1);
        byteArrayBuilder.appendBytes(inventoryItemPayload);
        return PublicKey.fromBytes(byteArrayBuilder);
    }

    public static Tuple<InventoryItemType, Sha256Hash> convertUtxoCommitmentPublicKeyToInventory(final PublicKey publicKey) {
        final InventoryItemType inventoryItemType;
        final Sha256Hash bucketHash;
        {
            final byte firstByte = publicKey.getByte(0);
            inventoryItemType = ((firstByte == PublicKey.COMPRESSED_FIRST_BYTE_0) ? InventoryItemType.UTXO_COMMITMENT_EVEN : InventoryItemType.UTXO_COMMITMENT_ODD);
            bucketHash = Sha256Hash.wrap(publicKey.getBytes(1, PublicKey.COMPRESSED_BYTE_COUNT - 1));
        }

        return new Tuple<>(inventoryItemType, bucketHash);
    }

    protected final MutableList<InventoryItem> _inventoryItems = new MutableList<>();

    public RequestDataMessage() {
        super(MessageType.REQUEST_DATA);
    }

    public List<InventoryItem> getInventoryItems() {
        return _inventoryItems;
    }

    public void addInventoryItem(final InventoryItem inventoryItem) {
        if (_inventoryItems.getCount() >= MAX_COUNT) { return; }
        _inventoryItems.add(inventoryItem);
    }

    public void clearInventoryItems() {
        _inventoryItems.clear();
    }

    @Override
    protected ByteArray _getPayload() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(CompactVariableLengthInteger.variableLengthIntegerToBytes(_inventoryItems.getCount()), Endian.BIG);
        for (final InventoryItem inventoryItem : _inventoryItems) {
            byteArrayBuilder.appendBytes(inventoryItem.getBytes(), Endian.BIG);
        }
        return byteArrayBuilder;
    }

    @Override
    protected Integer _getPayloadByteCount() {
        final ByteArray itemCountBytes = CompactVariableLengthInteger.variableLengthIntegerToBytes(_inventoryItems.getCount());
        return (itemCountBytes.getByteCount() + (_inventoryItems.getCount() * Sha256Hash.BYTE_COUNT));
    }
}
