package com.softwareverde.bitcoin.block.merkleroot;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.util.Util;

public class PartialMerkleTreeNode<T> {
    public interface Visitor<T> {
        void visit(PartialMerkleTreeNode<T> partialMerkleTreeNode);
    }

    public static int calculateMaxDepth(final int itemCount) {
        return (BitcoinUtil.log2(itemCount) + 1);
    }

    public static <T> PartialMerkleTreeNode<T> newRootNode(final int itemCount) {
        final int maxDepth = PartialMerkleTreeNode.calculateMaxDepth(itemCount);
        return new PartialMerkleTreeNode<T>(0, maxDepth);
    }

    protected PartialMerkleTreeNode(final int depth, final int maxDepth) {
        this.depth = depth;
        this.maxDepth = maxDepth;

        if (depth > maxDepth) { throw new IndexOutOfBoundsException(); }
    }

    public final int depth;
    public final int maxDepth;
    public PartialMerkleTreeNode<T> parent = null;
    public PartialMerkleTreeNode<T> left = null;
    public PartialMerkleTreeNode<T> right = null;
    public Sha256Hash value = null;

    // Optional Properties...
    public boolean include = true;
    public T object = null;

    public PartialMerkleTreeNode newLeftNode() {
        this.left = new PartialMerkleTreeNode<T>(depth + 1, this.maxDepth);
        this.left.parent = this;
        return this.left;
    }

    public PartialMerkleTreeNode newRightNode() {
        this.right = new PartialMerkleTreeNode<T>(depth + 1, this.maxDepth);
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

        final Sha256Hash leftHash = this.left.getHash();
        final Sha256Hash rightHash = (this.right == null ? leftHash : this.right.getHash());

        if (Util.areEqual(leftHash, rightHash)) {
            if (this.right != null) { return null; } // The left hash must not equal the right hash unless it was not provided...
        }

        return MerkleTreeNode.calculateNodeHash(leftHash, rightHash);
    }

    public void visit(final Visitor<T> visitor) {
        if (visitor != null) {
            visitor.visit(this);
        }

        final PartialMerkleTreeNode<T> left = this.left;
        if (left != null) {
            left.visit(visitor);
        }

        final PartialMerkleTreeNode<T> right = this.right;
        if (right != null) {
            right.visit(visitor);
        }
    }
}
