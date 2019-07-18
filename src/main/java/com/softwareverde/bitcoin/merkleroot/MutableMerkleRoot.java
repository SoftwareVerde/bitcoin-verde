package com.softwareverde.bitcoin.merkleroot;

import com.softwareverde.bitcoin.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;

public class MutableMerkleRoot extends MutableSha256Hash implements MerkleRoot {
    public static MutableMerkleRoot fromHexString(final String hexString) {
        final byte[] hashBytes = HexUtil.hexStringToByteArray(hexString);
        return new MutableMerkleRoot(hashBytes);
    }

    public static MutableMerkleRoot wrap(final byte[] bytes) {
        if (bytes.length != BYTE_COUNT) { return null; }
        return new MutableMerkleRoot(bytes);
    }

    public static MutableMerkleRoot copyOf(final byte[] bytes) {
        if (bytes.length != BYTE_COUNT) { return null; }
        return new MutableMerkleRoot(ByteUtil.copyBytes(bytes));
    }

    public static MutableMerkleRoot copyOf(final Sha256Hash hash) {
        return new MutableMerkleRoot(hash.getBytes());
    }

    public MutableMerkleRoot() {
        super();
    }

    protected MutableMerkleRoot(final byte[] bytes) {
        super(bytes);
    }

    public MutableMerkleRoot(final MerkleRoot merkleRoot) {
        super(merkleRoot);
    }

    @Override
    public ImmutableMerkleRoot asConst() {
        return new ImmutableMerkleRoot(this);
    }
}
