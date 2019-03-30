package com.softwareverde.bitcoin.block.merkleroot;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.MutableByteArray;

public class PartialMerkleNode {
    protected static Sha256Hash calculateMerkleHash(final Sha256Hash left, final Sha256Hash right) {
        final MutableByteArray mutableByteArray = new MutableByteArray(Sha256Hash.BYTE_COUNT * 2);
        ByteUtil.setBytes(mutableByteArray.unwrap(), left.toReversedEndian().getBytes());
        ByteUtil.setBytes(mutableByteArray.unwrap(), right.toReversedEndian().getBytes(), Sha256Hash.BYTE_COUNT);
        return BitcoinUtil.sha256(BitcoinUtil.sha256(mutableByteArray)).toReversedEndian();
    }

    public static PartialMerkleNode newRootNode(final int itemCount) {
        final int maxDepth = (BitcoinUtil.log2(itemCount) + 1);
        return new PartialMerkleNode(0, maxDepth);
    }

    protected PartialMerkleNode(final int depth, final int maxDepth) {
        this.depth = depth;
        this.maxDepth = maxDepth;

        if (depth > maxDepth) { throw new IndexOutOfBoundsException(); }
    }

    public PartialMerkleNode parent = null;
    public final int depth;
    public final int maxDepth;
    public PartialMerkleNode left = null;
    public PartialMerkleNode right = null;
    public Sha256Hash value = null;

    public PartialMerkleNode newLeftNode() {
        this.left = new PartialMerkleNode(depth + 1, this.maxDepth);
        this.left.parent = this;
        return this.left;
    }

    public PartialMerkleNode newRightNode() {
        this.right = new PartialMerkleNode(depth + 1, this.maxDepth);
        this.right.parent = this;
        return this.right;
    }

    public boolean isLeafNode() {
        return (this.depth == this.maxDepth);
    }

    public Sha256Hash getHash() {
        if (this.value != null) {
            return this.value;
        }

        return PartialMerkleNode.calculateMerkleHash(this.left.getHash(), this.right.getHash());
    }
}
