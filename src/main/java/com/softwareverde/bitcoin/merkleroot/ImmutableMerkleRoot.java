package com.softwareverde.bitcoin.merkleroot;

import com.softwareverde.constable.Const;
import com.softwareverde.cryptography.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.util.HexUtil;

public class ImmutableMerkleRoot extends ImmutableSha256Hash implements MerkleRoot, Const {
    public static ImmutableMerkleRoot fromHexString(final String hexString) {
        if (hexString == null) { return null; }

        final byte[] hashBytes = HexUtil.hexStringToByteArray(hexString);
        if (hashBytes == null) { return null; }
        if (hashBytes.length != BYTE_COUNT) { return null; }

        return new ImmutableMerkleRoot(hashBytes);
    }

    public static ImmutableMerkleRoot copyOf(final byte[] bytes) {
        if (bytes.length != BYTE_COUNT) { return null; }
        return new ImmutableMerkleRoot(bytes);
    }

    public ImmutableMerkleRoot() {
        super();
    }

    protected ImmutableMerkleRoot(final byte[] bytes) {
        super(bytes);
    }

    public ImmutableMerkleRoot(final MerkleRoot merkleRoot) {
        super(merkleRoot);
    }

    @Override
    public ImmutableMerkleRoot asConst() {
        return this;
    }
}
