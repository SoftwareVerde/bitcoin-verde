package com.softwareverde.bitcoin.block.merkleroot;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.merkleroot.MutableMerkleRoot;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.constable.Const;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;

public class PartialMerkleTree implements Const {
    public static PartialMerkleTree build(final Integer itemCount, final List<Sha256Hash> hashes, final ByteArray flags) {
        return new PartialMerkleTree(itemCount, hashes, flags);
    }

    protected final Integer _itemCount;
    protected final ByteArray _flags;
    protected final List<Sha256Hash> _hashes;

    protected MerkleRoot _cachedMerkleRoot;

    protected PartialMerkleTree(final Integer itemCount, final List<Sha256Hash> hashes, final ByteArray flags) {
        _itemCount = itemCount;
        _flags = flags.asConst();
        _hashes = hashes.asConst();
    }

    protected PartialMerkleTreeNode<Transaction> _buildPartialMerkleTree() {
        final int maxHashIndex = _hashes.getSize();
        final int maxFlagsIndex = (_flags.getByteCount() * 8);

        int flagIndex = 0;
        int hashIndex = 0;

        final PartialMerkleTreeNode<Transaction> rootMerkleNode = PartialMerkleTreeNode.newRootNode(_itemCount);
        PartialMerkleTreeNode currentNode = rootMerkleNode;
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

        return rootMerkleNode;
    }

    public List<Sha256Hash> getHashes() {
        return _hashes;
    }

    public ByteArray getFlags() {
        return _flags;
    }

    public synchronized MerkleRoot getMerkleRoot() {
        if (_cachedMerkleRoot == null) {
            final PartialMerkleTreeNode partialMerkleTreeRoot = _buildPartialMerkleTree();
            final Sha256Hash rootHash = partialMerkleTreeRoot.getHash();
            _cachedMerkleRoot = MutableMerkleRoot.wrap(rootHash.getBytes());
        }

        return _cachedMerkleRoot;
    }

    public synchronized PartialMerkleTreeNode<Transaction> getMerkleRootNode() {
        final PartialMerkleTreeNode<Transaction> partialMerkleTreeRoot = _buildPartialMerkleTree();

        if (_cachedMerkleRoot == null) {
            final Sha256Hash rootHash = partialMerkleTreeRoot.getHash();
            _cachedMerkleRoot = MutableMerkleRoot.wrap(rootHash.getBytes());
        }

        return partialMerkleTreeRoot;
    }

    public synchronized List<Sha256Hash> getTransactionHashes() {
        final PartialMerkleTreeNode<Transaction> partialMerkleTreeRoot = _buildPartialMerkleTree();

        if (_cachedMerkleRoot == null) {
            final Sha256Hash rootHash = partialMerkleTreeRoot.getHash();
            _cachedMerkleRoot = MutableMerkleRoot.wrap(rootHash.getBytes());
        }

        final MutableList<Sha256Hash> leafNodes = new MutableList<Sha256Hash>();
        partialMerkleTreeRoot.visit(new PartialMerkleTreeNode.Visitor<Transaction>() {
            @Override
            public void visit(final PartialMerkleTreeNode<Transaction> partialMerkleTreeNode) {
                if (partialMerkleTreeNode.isLeafNode()) {
                    leafNodes.add(partialMerkleTreeNode.value);
                }
            }
        });

        return leafNodes;
    }
}
