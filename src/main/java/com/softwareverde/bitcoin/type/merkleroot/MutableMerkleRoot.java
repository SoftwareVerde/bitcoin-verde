package com.softwareverde.bitcoin.type.merkleroot;

import com.softwareverde.bitcoin.type.hash.MutableHash;
import com.softwareverde.bitcoin.util.BitcoinUtil;

public class MutableMerkleRoot extends MutableHash implements MerkleRoot {
    public static MutableMerkleRoot fromHexString(final String hexString) {
        final byte[] hashBytes = BitcoinUtil.hexStringToByteArray(hexString);
        return new MutableMerkleRoot(hashBytes);
    }

    public MutableMerkleRoot() {
        super();
    }

    public MutableMerkleRoot(final byte[] bytes) {
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
