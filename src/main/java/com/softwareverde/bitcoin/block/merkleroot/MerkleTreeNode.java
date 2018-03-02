package com.softwareverde.bitcoin.block.merkleroot;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.type.hash.MutableHash;
import com.softwareverde.bitcoin.type.merkleroot.ImmutableMerkleRoot;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;

public class MerkleTreeNode implements MerkleTree {
    protected final byte[] _scratchSpace = new byte[Hash.BYTE_COUNT * 2];
    protected final MutableHash _hash = new MutableHash();

    protected int _childCount = 0;

    protected Transaction _transaction0 = null;
    protected Transaction _transaction1 = null;

    protected MerkleTreeNode _childNode0 = null;
    protected MerkleTreeNode _childNode1 = null;

    protected void _recalculateHash() {
        final Hash hash0;
        final Hash hash1;
        {
            if (_childCount == 0) {
                hash0 = new ImmutableHash();
                hash1 = hash0;
            }
            else if (_childCount < 3) {
                hash0 = _transaction0.calculateSha256Hash();
                hash1 = (_transaction1 == null ? hash0 : _transaction1.calculateSha256Hash());
            }
            else {
                hash0 = _childNode0.getMerkleRoot();
                hash1 = (_childNode1 == null ? hash0 : _childNode1.getMerkleRoot());
            }
        }

        ByteUtil.setBytes(_scratchSpace, hash0.toReversedEndian());
        ByteUtil.setBytes(_scratchSpace, hash1.toReversedEndian(), Hash.BYTE_COUNT);

        final byte[] doubleSha256HashConcatenatedBytes = BitcoinUtil.sha256(BitcoinUtil.sha256(_scratchSpace));
        _hash.setBytes(doubleSha256HashConcatenatedBytes);
    }

    protected MerkleTreeNode(final Transaction transaction0, final Transaction transaction1) {
        _transaction0 = transaction0;
        _transaction1 = transaction1;

        _childCount += (transaction0 == null ? 0 : 1);
        _childCount += (transaction1 == null ? 0 : 1);

        _recalculateHash();
    }

    public MerkleTreeNode() {
        _recalculateHash();
    }

    @Override
    public void addTransaction(final Transaction transaction) {
        if (_childCount == 0) {
            _transaction0 = transaction;
        }
        else if (_childCount == 1) {
            _transaction1 = transaction;
        }
        else if (_childCount == 2) {
            _childNode0 = new MerkleTreeNode(_transaction0, _transaction1);
            _childNode1 = new MerkleTreeNode(transaction, null);

            _transaction0 = null;
            _transaction1 = null;
        }
        else if (_childCount == 3) {
            _childNode1.addTransaction(transaction);
        }
        else {
            final MerkleTreeNode childNode = ( ((_childCount / 2) % 2 == 0) ? _childNode0 : _childNode1 );
            childNode.addTransaction(transaction);
        }

        _childCount += 1;
        _recalculateHash();
    }

    @Override
    public MerkleRoot getMerkleRoot() {
        return new ImmutableMerkleRoot(_hash.toReversedEndian());
    }
}
