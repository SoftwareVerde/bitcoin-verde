package com.softwareverde.bitcoin.block.merkleroot;

import com.softwareverde.bitcoin.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.merkleroot.MutableMerkleRoot;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;

public class MerkleTreeNode<T extends Hashable> implements MerkleTree<T> {
    protected static final ThreadLocal<MutableByteArray> _threadLocalScratchSpace = new ThreadLocal<MutableByteArray>() {
        @Override
        protected MutableByteArray initialValue() {
            return new MutableByteArray(Sha256Hash.BYTE_COUNT * 2);
        }
    };

    public static Sha256Hash calculateNodeHash(final Sha256Hash leftHash, final Sha256Hash rightHash) {
        final MutableByteArray scratchSpace = _threadLocalScratchSpace.get();

        ByteUtil.setBytes(scratchSpace.unwrap(), leftHash.toReversedEndian().getBytes());
        ByteUtil.setBytes(scratchSpace.unwrap(), rightHash.toReversedEndian().getBytes(), Sha256Hash.BYTE_COUNT);

        return BitcoinUtil.sha256(BitcoinUtil.sha256(scratchSpace)).toReversedEndian();
    }

    protected Boolean _hashIsValid = false;
    protected final MutableSha256Hash _hash = new MutableSha256Hash();

    protected int _itemCount = 0; // NOTE: _itemCount is the total number of items, which excludes intermediary hashes.

    protected T _item0 = null;
    protected T _item1 = null;

    protected MerkleTreeNode<T> _childNode0 = null;
    protected MerkleTreeNode<T> _childNode1 = null;

    protected MerkleRoot _getMerkleRoot() {
        if ((_itemCount == 1) && (_item0 != null)) {
            if (! _hashIsValid) {
                _hash.setBytes(_item0.getHash().getBytes());
                _hashIsValid = true;
            }
        }
        else {
            if (! _hashIsValid) {
                _recalculateHash();
            }
        }

        return MutableMerkleRoot.wrap(_hash.getBytes());
    }

    protected void _collectPartialHashes(final ImmutableListBuilder<Sha256Hash> listBuilder) {
        if (_itemCount == 0) { return; }
        else if (_item0 != null) {
            if (_item1 != null) {
                listBuilder.add(_item1.getHash());
            }
        }
        else {
            if ( (_childNode1 != null) && (! _childNode1.isEmpty()) ) {
                listBuilder.add(new ImmutableSha256Hash(_childNode1._getIntermediaryHash()));
            }
        }
    }

    protected void _getPartialTree(final int index, final ImmutableListBuilder<Sha256Hash> partialTreeBuilder) {
        if ( (_item0 != null) || (_item1 != null) ) {
            // Nothing.
        }
        else {
            final int childNode0ItemCount = _childNode0.getItemCount();
            if (index < childNode0ItemCount) {
                _childNode0._getPartialTree(index, partialTreeBuilder);
            }
            else {
                _childNode1._getPartialTree((index - childNode0ItemCount), partialTreeBuilder);
            }
        }

        _collectPartialHashes(partialTreeBuilder);
    }

    protected void _recalculateHash() {
        final Sha256Hash hash0;
        final Sha256Hash hash1;
        {
            if (_itemCount == 0) {
                hash0 = new ImmutableSha256Hash();
                hash1 = hash0;
            }
            else if (_item0 != null) {
                hash0 = _item0.getHash();
                hash1 = (_item1 == null ? hash0 : _item1.getHash());
            }
            else {
                hash0 = new ImmutableSha256Hash(_childNode0._getIntermediaryHash());
                hash1 = ((_childNode1 == null || _childNode1.isEmpty()) ? hash0 : new ImmutableSha256Hash(_childNode1._getIntermediaryHash()));
            }
        }

        _hash.setBytes(MerkleTreeNode.calculateNodeHash(hash0, hash1));
        _hashIsValid = true;
    }

    protected MerkleTreeNode(final MerkleTreeNode<T> childNode0, final MerkleTreeNode<T> childNode1) {
        _childNode0 = childNode0;
        _childNode1 = childNode1;

        _itemCount += (childNode0 == null ? 0 : childNode0.getItemCount());
        _itemCount += (childNode1 == null ? 0 : childNode1.getItemCount());

        _hashIsValid = false;
    }

    protected MerkleTreeNode(final T item0, final T item1) {
        _item0 = item0;
        _item1 = item1;

        _itemCount += (item0 == null ? 0 : 1);
        _itemCount += (item1 == null ? 0 : 1);

        _hashIsValid = false;
    }

    protected boolean _isBalanced() {
        if (_item0 != null) {
            return (_item1 != null);
        }
        else if (_childNode0 != null) {
            final int childNode1Size = ((_childNode1 == null) ? 0 : _childNode1.getItemCount());
            return (_childNode0.getItemCount() == childNode1Size);
        }
        return true; // Is empty...
    }

    protected MerkleTreeNode<T> _createChildNodeOfEqualDepth(final T item) {
        final int depth = BitcoinUtil.log2(_itemCount) - 1;

        final MerkleTreeNode<T> nodeOfEqualDepthToChildNode0;
        {
            MerkleTreeNode<T> merkleTreeNode = new MerkleTreeNode<T>(item, null);
            for (int i = 0; i < depth; ++i) {
                merkleTreeNode = new MerkleTreeNode<T>(merkleTreeNode, null);
            }
            nodeOfEqualDepthToChildNode0 = merkleTreeNode;
        }

        return nodeOfEqualDepthToChildNode0;
    }

    protected Sha256Hash _getIntermediaryHash() {
        if (! _hashIsValid) {
            _recalculateHash();
        }

        return _hash;
    }

    protected int _calculateItemCount() {
        int itemCount = 0;
        itemCount += (_item0 != null ? 1 : 0);
        itemCount += (_item1 != null ? 1 : 0);
        itemCount += (_childNode0 != null ? _childNode0._calculateItemCount() : 0);
        itemCount += (_childNode1 != null ? _childNode1._calculateItemCount() : 0);
        return itemCount;
    }

    private void _addItemsTo(final ImmutableListBuilder<T> immutableListBuilder) {
        if (_item0 != null) {
            immutableListBuilder.add(_item0);

            if (_item1 != null) {
                immutableListBuilder.add(_item1);
            }
        }
        else {
            if (_childNode0 != null) {
                _childNode0._addItemsTo(immutableListBuilder);

                if (_childNode1 != null) {
                    _childNode1._addItemsTo(immutableListBuilder);
                }
            }
        }
    }

    protected PartialMerkleTreeNode<T> _toPartialMerkleTreeNode(final int depth, final int maxDepth) {
        final PartialMerkleTreeNode<T> partialMerkleTreeNode = new PartialMerkleTreeNode<T>(depth, maxDepth);

        if (_item0 != null) {
            partialMerkleTreeNode.newLeftNode();
            partialMerkleTreeNode.left.value = _item0.getHash();
            partialMerkleTreeNode.left.object = _item0;
        }
        else if (_childNode0 != null) {
            partialMerkleTreeNode.left = _childNode0._toPartialMerkleTreeNode(depth + 1, maxDepth);
            partialMerkleTreeNode.left.parent = partialMerkleTreeNode;
        }

        if (_item1 != null) {
            partialMerkleTreeNode.newRightNode();
            partialMerkleTreeNode.right.value = _item1.getHash();
            partialMerkleTreeNode.right.object = _item1;
        }
        else if (_childNode1 != null) {
            partialMerkleTreeNode.right = _childNode1._toPartialMerkleTreeNode(depth + 1, maxDepth);
            partialMerkleTreeNode.right.parent = partialMerkleTreeNode;
        }

        return partialMerkleTreeNode;
    }

    public MerkleTreeNode() {
        _hashIsValid = false;
    }

    public void clear() {
        _itemCount = 0;
        _hashIsValid = false;
        _item0 = null;
        _item1 = null;
        _childNode0 = null;
        _childNode1 = null;
    }

    @Override
    public void addItem(final T item) {
        if (_itemCount == 0) {
            _item0 = item;
        }
        else if (_item0 != null) {
            if (_item1 == null) {
                _item1 = item;
            }
            else {
                _childNode0 = new MerkleTreeNode<T>(_item0, _item1);
                _childNode1 = new MerkleTreeNode<T>(item, null);

                _item0 = null;
                _item1 = null;
            }
        }
        else {
            if (_isBalanced()) {
                _childNode0 = new MerkleTreeNode<T>(_childNode0, _childNode1);
                _childNode1 = _createChildNodeOfEqualDepth(item);
            }
            else {
                if (_childNode0._isBalanced()) {
                    if (_childNode1 == null) {
                        _childNode1 = _createChildNodeOfEqualDepth(item);
                    }
                    else {
                        _childNode1.addItem(item);
                    }
                }
                else {
                    _childNode0.addItem(item);
                }
            }
        }

        _itemCount += 1;
        _hashIsValid = false;
    }

    @Override
    public T getItem(final int index) {
        if ( (_item0 != null) || (_item1 != null) ) {
            if (index == 0) {
                return _item0;
            }
            else if (index == 1) {
                return _item1;
            }
        }
        else {
            final int childNode0ItemCount = _childNode0.getItemCount();
            if (index < childNode0ItemCount) {
                return _childNode0.getItem(index);
            }
            else {
                return _childNode1.getItem(index - childNode0ItemCount);
            }
        }

        return null; // Invalid state...
    }

    @Override
    public List<T> getItems() {
        final ImmutableListBuilder<T> immutableListBuilder = new ImmutableListBuilder<T>(_itemCount);
        _addItemsTo(immutableListBuilder);
        return immutableListBuilder.build();
    }

    @Override
    public void replaceItem(final int index, final T item) {
        if ( (_item0 != null) || (_item1 != null) ) {
            if (index == 0) {
                _item0 = item;
            }
            else if (index == 1) {
                _item1 = item;
            }
        }
        else {
            final int childNode0ItemCount = _childNode0.getItemCount();
            if (index < childNode0ItemCount) {
                _childNode0.replaceItem(index, item);
            }
            else {
                _childNode1.replaceItem((index - childNode0ItemCount), item);
            }
        }

        _hashIsValid = false;
    }

    @Override
    public int getItemCount() {
        return _itemCount;
    }

    @Override
    public boolean isEmpty() {
        return (_itemCount == 0);
    }

    @Override
    public MerkleRoot getMerkleRoot() {
        return _getMerkleRoot();
    }

    @Override
    public List<Sha256Hash> getPartialTree(final int index) {
        final ImmutableListBuilder<Sha256Hash> partialTreeBuilder = new ImmutableListBuilder<Sha256Hash>();
        _getPartialTree(index, partialTreeBuilder);
        return partialTreeBuilder.build();
    }

    @Override
    public PartialMerkleTree getPartialTree(final Filter<T> filter) {
        final int maxDepth = PartialMerkleTreeNode.calculateMaxDepth(_itemCount);
        final PartialMerkleTreeNode<T> rootPartialMerkleTreeNode = _toPartialMerkleTreeNode(0, maxDepth);

        final MutableList<PartialMerkleTreeNode<T>> leafNodes = new MutableList<PartialMerkleTreeNode<T>>(_itemCount);
        rootPartialMerkleTreeNode.visit(new PartialMerkleTreeNode.Visitor<T>() {
            @Override
            public void visit(final PartialMerkleTreeNode<T> partialMerkleTreeNode) {
                final T object = partialMerkleTreeNode.object;
                if (object == null) {
                    partialMerkleTreeNode.include = false;
                }
                else {
                    partialMerkleTreeNode.include = filter.shouldInclude(object);
                }

                if (partialMerkleTreeNode.isLeafNode()) {
                    leafNodes.add(partialMerkleTreeNode);
                }
            }
        });

        for (final PartialMerkleTreeNode<T> leafNode : leafNodes) {
            if (! leafNode.include) { continue; }

            PartialMerkleTreeNode<T> currentNode = leafNode;
            while (currentNode != null) {
                currentNode.include = true;
                currentNode = currentNode.parent;

                if ( (currentNode != null) && (currentNode.include) ) { break; }
            }
        }

        final MutableList<Sha256Hash> hashes = new MutableList<Sha256Hash>();
        final MutableList<Boolean> flagBits = new MutableList<Boolean>();
        rootPartialMerkleTreeNode.visit(new PartialMerkleTreeNode.Visitor<T>() {
            @Override
            public void visit(final PartialMerkleTreeNode<T> partialMerkleTreeNode) {
                if (partialMerkleTreeNode.isLeafNode()) {
                    if ( (partialMerkleTreeNode.include) || ((partialMerkleTreeNode.parent != null) && (partialMerkleTreeNode.parent.include)) ) {
                        hashes.add(partialMerkleTreeNode.value);
                        flagBits.add(partialMerkleTreeNode.include);
                    }
                }
                else {
                    if ( (! partialMerkleTreeNode.include) && ((partialMerkleTreeNode.parent != null) && (partialMerkleTreeNode.parent.include)) ) {
                        hashes.add(partialMerkleTreeNode.getHash());
                    }

                    if ( (partialMerkleTreeNode.include) || ((partialMerkleTreeNode.parent != null) && (partialMerkleTreeNode.parent.include)) ) {
                        flagBits.add(partialMerkleTreeNode.include);
                    }
                }
            }
        });

        final ByteArray flags;
        if (hashes.isEmpty()) {
            final MerkleRoot merkleRoot = _getMerkleRoot();
            hashes.add(merkleRoot);
            flags = new MutableByteArray(1);
        }
        else {
            final MutableByteArray mutableFlags = new MutableByteArray((flagBits.getCount() + 7) / 8);
            for (int i = 0; i < flagBits.getCount(); ++i) {
                mutableFlags.setBit(i, flagBits.get(i));
            }
            flags = mutableFlags;
        }

        return PartialMerkleTree.build(_itemCount, hashes, flags);
    }
}
