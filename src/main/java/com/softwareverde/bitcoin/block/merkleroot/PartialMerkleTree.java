package com.softwareverde.bitcoin.block.merkleroot;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.merkleroot.MutableMerkleRoot;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;

public class PartialMerkleTree {
    public static PartialMerkleTree build(final Integer itemCount, final List<Sha256Hash> hashes, final ByteArray flags) {
        return new PartialMerkleTree(itemCount, hashes, flags);
    }

    protected static int _calculateTreeWidth(final int totalItemCount, final int depth) {
//        final int height = depth; // ((BitcoinUtil.log2(totalItemCount) - 1) - depth);
//        return ((totalItemCount + (1 << height) - 1) >> height);
        return (int) Math.pow(2, depth);
    }

    protected final Integer _itemCount;
    protected Integer _flagCount = 0;
    protected final MutableByteArray _flags;
    protected final MutableList<Sha256Hash> _hashes;

    protected PartialMerkleTree(final Integer itemCount, final List<Sha256Hash> hashes, final ByteArray flags) {
        _itemCount = itemCount;
        _flags = new MutableByteArray(flags);
        _hashes = new MutableList<Sha256Hash>(hashes);
        _flagCount = _flags.getByteCount();
    }

    public PartialMerkleTree(final Integer itemCount) {
        _itemCount = itemCount;
        _flags = new MutableByteArray((itemCount + 7) / 8);
        _hashes = new MutableList<Sha256Hash>(itemCount);
    }

    public void includeLeaf(final Sha256Hash itemHash) {
        _hashes.add(itemHash);
        _flags.setBit(_flagCount, true);
        _flagCount += 1;
    }

    public void excludeLeaf(final Sha256Hash itemHash) {
        _hashes.add(itemHash);
        _flags.setBit(_flagCount, false);
        _flagCount += 1;
    }

    public void includeNode(final Sha256Hash nodeHash) {
        _hashes.add(nodeHash);
        _flags.setBit(_flagCount, true);
        _flagCount += 1;
    }

    public List<Sha256Hash> getHashes() {
        return _hashes;
    }

    public ByteArray getFlags() {
        return _flags;
    }

    public MerkleRoot getMerkleRoot() {
        final int maxHashIndex = _hashes.getSize();
        final int maxFlagsIndex = (_flags.getByteCount() * 8);

        int flagIndex = 0;
        int hashIndex = 0;

        final PartialMerkleNode rootMerkleNode = PartialMerkleNode.newRootNode(_itemCount);
        PartialMerkleNode currentNode = rootMerkleNode;
        while (currentNode != null) {
            if (hashIndex >= maxHashIndex) { break; }
            if (flagIndex >= maxFlagsIndex) { break; }

            if ( (currentNode.left != null) && (currentNode.right != null) ) {
                currentNode = currentNode.parent;
                if (currentNode == null) { break; }
                continue;
            }

            if (currentNode.left != null) {
                currentNode = currentNode.newRightNode();
            }

            final boolean isMatchOrParentOfMatch = _flags.getBit(flagIndex);
            flagIndex += 1;

            if (isMatchOrParentOfMatch) {
                if (currentNode.isLeafNode()) {
                    currentNode.value = _hashes.get(hashIndex);
                    hashIndex += 1;

                    currentNode = currentNode.parent;
                }
                else {
                    if (currentNode.left == null) {
                        currentNode = currentNode.newLeftNode();
                    }
                    else if (currentNode.right == null) {
                        currentNode = currentNode.newRightNode();
                    }
                    else {
                        currentNode = currentNode.parent;
                    }
                }
            }
            else {
                currentNode.value = _hashes.get(hashIndex);
                hashIndex += 1;

                currentNode = currentNode.parent;
            }
        }

        if (hashIndex != maxHashIndex) { return null; } // All hashes were not consumed...
        for (int i = flagIndex; i < maxFlagsIndex; ++i) {
            final boolean flag = _flags.getBit(i);
            if (flag) { return null; } // All non-padding flag bits were not consumed...
        }

        return MutableMerkleRoot.wrap(rootMerkleNode.getHash().getBytes());
    }
}
