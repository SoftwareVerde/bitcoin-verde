package com.softwareverde.bitcoin.block.merkleroot;

import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.type.hash.MutableHash;
import com.softwareverde.bitcoin.type.merkleroot.ImmutableMerkleRoot;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;

import java.util.ArrayList;
import java.util.List;

/*
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

                                                    _ ABCDEFGHIJKKIJIJ _
                                                   /                    \
                                           ABCDEFGH                      IJKKIJIJ
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
            else if (_size < 3) {
                hash0 = _item0.calculateSha256Hash();
                hash1 = (_item1 == null ? hash0 : _item1.calculateSha256Hash());
            }
            else {
                hash0 = _childNode0.getMerkleRoot();
                hash1 = (_childNode1 == null ? hash0 : _childNode1.getMerkleRoot());
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

    public MerkleTreeNode() {
        _recalculateHash();
    }

    public int getSize() {
        return _size;
    }

    @Override
    public void addItem(final Hashable item) {
        if (_size == 0) {
            _item0 = item;
        }
        else if (_size == 1) {
            _item1 = item;
        }
        else if (_size == 2) {
            _childNode0 = new MerkleTreeNode(_item0, _item1);
            _childNode1 = new MerkleTreeNode(item, null);

            _item0 = null;
            _item1 = null;
        }
        else if (_size == 3) {
            _childNode1.addItem(item);
        }
        else {
            if (_childNode0.getSize() == _childNode1.getSize()) {
                final MerkleTreeNode newMerkleTreeNode = new MerkleTreeNode(_childNode0, _childNode1);
                _childNode0 = newMerkleTreeNode;
                _childNode1 = new MerkleTreeNode(item, null);
            }
            else {
                _childNode1.addItem(item);
            }
        }

        _size += 1;
        _recalculateHash();
    }

    @Override
    public MerkleRoot getMerkleRoot() {
        return new ImmutableMerkleRoot(_hash.toReversedEndian());
    }

    public List<byte[]> collectItems() {
        final List<byte[]> list = new ArrayList<byte[]>();

        if (_item0 != null) { list.add(_item0.calculateSha256Hash().getBytes()); }
        if (_item1 != null) { list.add(_item1.calculateSha256Hash().getBytes()); }
        if (_childNode0 != null) { list.addAll(_childNode0.collectItems()); }
        if (_childNode1 != null) { list.addAll(_childNode1.collectItems()); }

        return list;
    }

    public List<byte[]> collectHashes() {
        final List<byte[]> list = new ArrayList<byte[]>();

        if (_item0 != null || _item1 != null) {
            list.add(_hash.getBytes());
        }
        else {
            list.addAll(_childNode0.collectHashes());
            list.addAll(_childNode1.collectHashes());
        }

        return list;
    }
}




/*
-------------- 1
    313327D643CD873A707353B79E33462178ADBDF1F95827AA775F149135842092

-------------- 2
    8410AF673CD3BA6BF31DD667492809B26072680E51584D5B3E03ABC25DBE7FD9
    0334A7CE1B829FC6D327A448C8B03900669D47372903FC10D605EE0B3D4406A4

-------------- 3
    513F693ADAD3F522DCBFFAD323A427454F609D1CE5DE80308E18CD6F9506F999
    3118C6AB44076F9027116A4949E5C0CCA41389A093D98D5371A154C0E5C992F0
    3A7058ADCD0CF8B5EEB029C3BDED44C0919DA355B2F7EA2721B85409376BD770

-------------- 6
    35EC3E69FBE385752774E96833EC5CE21CCABC8AD73CBCECEDA83C37F757581A
    1DD9DA358BF6CA2478DC80E184B840222F6DCDAFC6246F346D3FEB80EED4F68A
    370CF3413D8711D67A84C60D76587A620EFC2B2A42F5E3FF8AA40783036A5CEB
    7AC2C70EAF84C00379A8DFAB252AFE07A8CFBCFC3A45F8418153C78865DCF152
    C3A705A976D86C26A525314E49DC726628217B55378E00F5C816805E1AA126EE
    05F8C0C4554400CCFB3D9E8CA8D8A7C968D627442CF2855BA717C11F653D1873

-------------- 12
    0000000000000000000000000000000000000000000000000000000000000000
    0000000100000000000000000000000000000000000000000000000000000000
    0000000200000000000000000000000000000000000000000000000000000000
    0000000300000000000000000000000000000000000000000000000000000000
    0000000400000000000000000000000000000000000000000000000000000000
    0000000500000000000000000000000000000000000000000000000000000000
    0000000600000000000000000000000000000000000000000000000000000000
    0000000700000000000000000000000000000000000000000000000000000000
    0000000800000000000000000000000000000000000000000000000000000000
    0000000900000000000000000000000000000000000000000000000000000000
    0000000A00000000000000000000000000000000000000000000000000000000
    0000000B00000000000000000000000000000000000000000000000000000000




-------------- 1
    9003E04C3D85218A4EA5C83FF2914D8E413754A74DD2A558877EB10C4DC01440

-------------- 2
    8410AF673CD3BA6BF31DD667492809B26072680E51584D5B3E03ABC25DBE7FD9
    AFB45E44C504170DE2E3A612A888B20509CF02BD019E7541D6D70BC250CE1B81

-------------- 4
    513F693ADAD3F522DCBFFAD323A427454F609D1CE5DE80308E18CD6F9506F999
    3118C6AB44076F9027116A4949E5C0CCA41389A093D98D5371A154C0E5C992F0
    3A7058ADCD0CF8B5EEB029C3BDED44C0919DA355B2F7EA2721B85409376BD770
    33FEC6903E3B5823F6ADADB7A2F02B9FCFAA46B2DB43B86757D1D4EEF212C7D3

-------------- 7
    35EC3E69FBE385752774E96833EC5CE21CCABC8AD73CBCECEDA83C37F757581A
    1DD9DA358BF6CA2478DC80E184B840222F6DCDAFC6246F346D3FEB80EED4F68A
    370CF3413D8711D67A84C60D76587A620EFC2B2A42F5E3FF8AA40783036A5CEB
    7AC2C70EAF84C00379A8DFAB252AFE07A8CFBCFC3A45F8418153C78865DCF152
    C3A705A976D86C26A525314E49DC726628217B55378E00F5C816805E1AA126EE
    05F8C0C4554400CCFB3D9E8CA8D8A7C968D627442CF2855BA717C11F653D1873
    6E911D48B290D53CDE8F5B95D2AA675FE85C1B42CF64B63F11903EB457D22C98

-------------- 13
    0000000000000000000000000000000000000000000000000000000000000000
    0000000100000000000000000000000000000000000000000000000000000000
    0000000200000000000000000000000000000000000000000000000000000000
    0000000300000000000000000000000000000000000000000000000000000000
    0000000400000000000000000000000000000000000000000000000000000000
    0000000500000000000000000000000000000000000000000000000000000000
    0000000600000000000000000000000000000000000000000000000000000000
    0000000700000000000000000000000000000000000000000000000000000000
    0000000800000000000000000000000000000000000000000000000000000000
    0000000900000000000000000000000000000000000000000000000000000000
    0000000A00000000000000000000000000000000000000000000000000000000
    0000000B00000000000000000000000000000000000000000000000000000000
    0000000C00000000000000000000000000000000000000000000000000000000
 */
