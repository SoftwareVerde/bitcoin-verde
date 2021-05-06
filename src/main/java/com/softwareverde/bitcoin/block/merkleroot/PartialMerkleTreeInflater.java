package com.softwareverde.bitcoin.block.merkleroot;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.cryptography.hash.sha256.MutableSha256Hash;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.bytearray.Endian;

public class PartialMerkleTreeInflater {
    public static final Integer MAX_HASHES_COUNT = (1 << 17);

    protected PartialMerkleTree _fromBytes(final ByteArrayReader byteArrayReader) {

        final Integer transactionCount = byteArrayReader.readInteger(4, Endian.LITTLE);

        final Integer hashesCount = byteArrayReader.readVariableLengthInteger().intValue();
        if (hashesCount > MAX_HASHES_COUNT) {
            Logger.debug("MerkleBlock exceeded maximum hashes count: " + hashesCount);
            return null;
        }

        final ImmutableListBuilder<Sha256Hash> hashesBuilder = new ImmutableListBuilder<Sha256Hash>(hashesCount);
        for (int i = 0; i < hashesCount; ++i) {
            final Sha256Hash hash = MutableSha256Hash.wrap(byteArrayReader.readBytes(Sha256Hash.BYTE_COUNT, Endian.LITTLE));
            hashesBuilder.add(hash);
        }

        final Integer flagsByteCount = byteArrayReader.readVariableLengthInteger().intValue();
        if (flagsByteCount > MAX_HASHES_COUNT) {
            Logger.debug("MerkleBlock exceeded maximum flag-bytes count: " + flagsByteCount);
            return null;
        }

        final MutableByteArray flags = MutableByteArray.wrap(byteArrayReader.readBytes(flagsByteCount));
        for (int i = 0; i < flagsByteCount; ++i) {
            flags.setByte(i, ByteUtil.reverseBits(flags.getByte(i)));
        }

        if (byteArrayReader.didOverflow()) { return null; }

        return PartialMerkleTree.build(transactionCount, hashesBuilder.build(), flags);
    }

    public PartialMerkleTree fromBytes(final byte[] bytes) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        return _fromBytes(byteArrayReader);
    }

    public PartialMerkleTree fromBytes(final ByteArray bytes) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        return _fromBytes(byteArrayReader);
    }

    public PartialMerkleTree fromBytes(final ByteArrayReader byteArrayReader) {
        return _fromBytes(byteArrayReader);
    }
}
