package com.softwareverde.bitcoin.block.merkleroot;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class PartialMerkleTreeDeflater {
    public ByteArray toBytes(final PartialMerkleTree partialMerkleTree) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(partialMerkleTree.getItemCount()), Endian.LITTLE); // Aka the BlockHeader's Transaction count...

        final List<Sha256Hash> hashes = partialMerkleTree.getHashes();
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(hashes.getCount()));
        for (final Sha256Hash hash : hashes) {
            byteArrayBuilder.appendBytes(hash, Endian.LITTLE);
        }

        final ByteArray flags = partialMerkleTree.getFlags();
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(flags.getByteCount()));

        for (int i = 0; i < flags.getByteCount(); ++i) {
            byteArrayBuilder.appendByte(ByteUtil.reverseBits(flags.getByte(i)));
        }

        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}
