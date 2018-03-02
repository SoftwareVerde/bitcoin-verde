package com.softwareverde.bitcoin.block.merkleroot;

import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.type.hash.MutableHash;
import com.softwareverde.bitcoin.type.merkleroot.ImmutableMerkleRoot;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;

/*

                                                               A

                                                              AB
                                                             /  \
                                                            A    B

                                                             ABCC
                                                            /    \
                                                          AB      CC
                                                         /  \    /  \
                                                        A    B  C   [ ]

                                                             ABCD
                                                            /    \
                                                          AB      CD
                                                         /  \    /  \
                                                        A    B  C    D

                                                           ABCDEEEE
                                                          /        \
                                                      ABCD          EEEE
                                                     /    \        /    \
                                                   AB      CD    EE     [ ]
                                                  /  \    /  \  /  \
                                                 A    B  C   D E   [ ]

                                                           ABCDEFEF
                                                          /        \
                                                      ABCD          EFEF
                                                     /    \        /    \
                                                   AB      CD    EF     [ ]
                                                  /  \    /  \  /  \
                                                 A    B  C   D E   F

                                                           ABCDEFGG
                                                          /        \
                                                      ABCD          EFGG
                                                     /    \        /    \
                                                   AB      CD    EF      GG
                                                  /  \    /  \  /  \    /  \
                                                 A    B  C   D E   F   G   [ ]

                                                           ABCDEFGH
                                                          /        \
                                                      ABCD          EFGH
                                                     /    \        /    \
                                                   AB      CD    EF      GH
                                                  /  \    /  \  /  \    /  \
                                                 A    B  C   D E   F   G   H

                                                    _ ABCDEFGHIIIIIIII _
                                                   /                    \
                                           ABCDEFGH                      IIIIIIII
                                          /        \                    /        \
                                      ABCD          EFGH            IIII          [ ]
                                     /    \        /    \          /    \
                                   AB      CD    EF      GH      II     [ ]
                                  /  \    /  \  /  \    /  \    /  \
                                 A    B  C   D E   F   G   H   I   [ ]

                                                    _ ABCDEFGHIJIJIJIJ _
                                                   /                    \
                                           ABCDEFGH                      IJIJIJIJ
                                          /        \                    /        \
                                      ABCD          EFGH            IJIJ          [ ]
                                     /    \        /    \          /    \
                                   AB      CD    EF      GH      IJ     [ ]
                                  /  \    /  \  /  \    /  \    /  \
                                 A    B  C   D E   F   G   H   I    J

                                                    _ ABCDEFGHIJKKIJKK _
                                                   /                    \
                                           ABCDEFGH                      IJKKIJKK
                                          /        \                    /        \
                                      ABCD          EFGH            IJKK          [ ]
                                     /    \        /    \          /    \
                                   AB      CD    EF      GH      IJ      KK
                                  /  \    /  \  /  \    /  \    /  \    /  \
                                 A    B  C   D E   F   G   H   I    J  K   [ ]

                                                    _ ABCDEFGHIJKLIJKL _
                                                   /                    \
                                           ABCDEFGH                      IJKLIJKL
                                          /        \                    /        \
                                      ABCD          EFGH            IJKL          [ ]
                                     /    \        /    \          /    \
                                   AB      CD    EF      GH      IJ      KL
                                  /  \    /  \  /  \    /  \    /  \    /  \
                                 A    B  C   D E   F   G   H   I    J  K    L

                                                    _ ABCDEFGHIJKLMMMM _
                                                   /                    \
                                           ABCDEFGH                      IJKLMMMM
                                          /        \                    /        \
                                      ABCD          EFGH            IJKL          MMMM
                                     /    \        /    \          /    \        /    \
                                   AB      CD    EF      GH      IJ      KL    MM     [ ]
                                  /  \    /  \  /  \    /  \    /  \    /  \  /  \
                                 A    B  C   D E   F   G   H   I    J  K    L M  [ ]
 */

public class MerkleTreeNode implements MerkleTree {
    protected final byte[] _scratchSpace = new byte[Hash.BYTE_COUNT * 2];
    protected final MutableHash _hash = new MutableHash();

    protected int _size = 0;

    protected Hashable _item0 = null;
    protected Hashable _item1 = null;

    protected MerkleTreeNode _childNode0 = null;
    protected MerkleTreeNode _childNode1 = null;

    protected void _recalculateHash() {
        final Hash hash0;
        final Hash hash1;
        {
            if (_size == 0) {
                hash0 = new ImmutableHash();
                hash1 = hash0;
            }
            else if (_item0 != null) {
                hash0 = _item0.calculateSha256Hash();
                hash1 = (_item1 == null ? hash0 : _item1.calculateSha256Hash());
            }
            else {
                hash0 = new ImmutableHash(_childNode0.getMerkleRoot());
                hash1 = ((_childNode1 == null || _childNode1.isEmpty()) ? hash0 : new ImmutableHash(_childNode1.getMerkleRoot()));
            }
        }

        ByteUtil.setBytes(_scratchSpace, hash0.toReversedEndian());
        ByteUtil.setBytes(_scratchSpace, hash1.toReversedEndian(), Hash.BYTE_COUNT);

        final byte[] doubleSha256HashConcatenatedBytes = ByteUtil.reverseEndian(BitcoinUtil.sha256(BitcoinUtil.sha256(_scratchSpace)));
        _hash.setBytes(doubleSha256HashConcatenatedBytes);
    }

    protected MerkleTreeNode(final MerkleTreeNode childNode0, final MerkleTreeNode childNode1) {
        _childNode0 = childNode0;
        _childNode1 = childNode1;

        _size += (childNode0 == null ? 0 : childNode0.getSize());
        _size += (childNode1 == null ? 0 : childNode1.getSize());

        _recalculateHash();
    }

    protected MerkleTreeNode(final Hashable item0, final Hashable item1) {
        _item0 = item0;
        _item1 = item1;

        _size += (item0 == null ? 0 : 1);
        _size += (item1 == null ? 0 : 1);

        _recalculateHash();
    }

    protected boolean _isBalanced() {
        if (_item0 != null) {
            return (_item1 != null);
        }
        else if (_childNode0 != null) {
            final int childNode1Size = ((_childNode1 == null) ? 0 : _childNode1.getSize());
            return (_childNode0.getSize() == childNode1Size);
        }
        return true; // Is empty...
    }

    protected MerkleTreeNode _createChildNodeOfEqualDepth(final Hashable item) {
        final int depth = BitcoinUtil.log2(_size) - 1;

        final MerkleTreeNode nodeOfEqualDepthToChildNode0;
        {
            MerkleTreeNode merkleTreeNode = new MerkleTreeNode(item, null);
            for (int i = 0; i < depth; ++i) {
                merkleTreeNode = new MerkleTreeNode(merkleTreeNode, null);
            }
            nodeOfEqualDepthToChildNode0 = merkleTreeNode;
        }

        return nodeOfEqualDepthToChildNode0;
    }

    public MerkleTreeNode() {
        _recalculateHash();
    }

    public int getSize() {
        return _size;
    }

    public boolean isEmpty() {
        return (_size == 0);
    }

    @Override
    public void addItem(final Hashable item) {
        if (_size == 0) {
            _item0 = item;
        }
        else if (_item0 != null) {
            if (_item1 == null) {
                _item1 = item;
            }
            else {
                _childNode0 = new MerkleTreeNode(_item0, _item1);
                _childNode1 = new MerkleTreeNode(item, null);

                _item0 = null;
                _item1 = null;
            }
        }
        else {
            if (_isBalanced()) {
                final MerkleTreeNode newMerkleTreeNode = new MerkleTreeNode(_childNode0, _childNode1);
                _childNode0 = newMerkleTreeNode;
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

        _size += 1;
        _recalculateHash();
    }

    @Override
    public MerkleRoot getMerkleRoot() {
        return new ImmutableMerkleRoot(_hash.getBytes());
    }
}
