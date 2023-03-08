package com.softwareverde.bitcoin.block.merkleroot;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class PartialMerkleTreeDeflater {
    public ByteArray toBytes(final PartialMerkleTree partialMerkleTree) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(partialMerkleTree.getItemCount()), Endian.LITTLE); // Aka the BlockHeader's Transaction count...

        final List<Sha256Hash> hashes = partialMerkleTree.getHashes();
        byteArrayBuilder.appendBytes(CompactVariableLengthInteger.variableLengthIntegerToBytes(hashes.getCount()));
        for (final Sha256Hash hash : hashes) {
            byteArrayBuilder.appendBytes(hash, Endian.LITTLE);
        }

        final ByteArray flags = partialMerkleTree.getFlags();
        byteArrayBuilder.appendBytes(CompactVariableLengthInteger.variableLengthIntegerToBytes(flags.getByteCount()));

        for (int i = 0; i < flags.getByteCount(); ++i) {
            byteArrayBuilder.appendByte(ByteUtil.reverseBits(flags.getByte(i)));
        }

        return MutableByteArray.wrap(byteArrayBuilder.build());
    }

    public Integer getByteCount(final PartialMerkleTree partialMerkleTree) {
        final int blockHeaderTransactionCountByteCount = 4;

        final List<Sha256Hash> hashes = partialMerkleTree.getHashes();
        final int itemCount = hashes.getCount();
        final ByteArray itemCountBytes = CompactVariableLengthInteger.variableLengthIntegerToBytes(itemCount);
        final ByteArray flags = partialMerkleTree.getFlags();

        return (blockHeaderTransactionCountByteCount + itemCountBytes.getByteCount() + (itemCount * Sha256Hash.BYTE_COUNT) + flags.getByteCount());
    }
}
