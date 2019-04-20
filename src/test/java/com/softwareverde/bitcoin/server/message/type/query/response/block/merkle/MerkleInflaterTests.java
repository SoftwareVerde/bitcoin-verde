package com.softwareverde.bitcoin.server.message.type.query.response.block.merkle;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

public class MerkleInflaterTests {
    @Test
    public void should_inflate_merkle_block_from_bitcoinj() {
        final String merkleBlockBytesString = "E3E1F3E86D65726B6C65626C6F636B0058010000F9F75FF7" + "0100000006E533FD1ADA86391F3F6C343204B0D278D4AAEC1C0B20AA27BA0300000000006ABBB3EB3D733A9FE18967FD7D4C117E4CCBBAC5BEC4D910D900B3AE0793E77F54241B4D4C86041B4089CC9B0C000000084C30B63CFCDC2D35E3329421B9805EF0C6565D35381CA857762EA0B3A5A128BBCA5065FF9617CBCBA45EB23726DF6498A9B9CAFED4F54CBAB9D227B0035DDEFBBB15AC1D57D0182AAEE61C74743A9C4F785895E563909BAFEC45C9A2B0FF3181D77706BE8B1DCC91112EADA86D424E2D0A8907C3488B6E44FDA5A74A25CBC7D6BB4FA04245F4AC8A1A571D5537EAC24ADCA1454D65EDA446055479AF6C6D4DD3C9AB658448C10B6921B7A4CE3021EB22ED6BB6A7FDE1E5BCC4B1DB6615C6ABC5CA042127BFAF9F44EBCE29CB29C6DF9D05B47F35B2EDFF4F0064B578AB741FA78276222651209FE1A2C4C0FA1C58510AEC8B090DD1EB1F82F9D261B8273B525B02FF1A";
        final MerkleBlockMessageInflater merkleBlockMessageInflater = new MerkleBlockMessageInflater();

        final MerkleBlockMessage inflatedMerkleBlockMessage = merkleBlockMessageInflater.fromBytes(HexUtil.hexStringToByteArray(merkleBlockBytesString));

        final MerkleBlockMessage merkleBlockMessage = new MerkleBlockMessage();
        merkleBlockMessage.setBlockHeader(inflatedMerkleBlockMessage.getBlockHeader());
        merkleBlockMessage.setPartialMerkleTree(inflatedMerkleBlockMessage.getPartialMerkleTree());

        Assert.assertEquals(inflatedMerkleBlockMessage.getPartialMerkleTree().getFlags(), merkleBlockMessage.getPartialMerkleTree().getFlags());
        int i = 0;
        for (final Sha256Hash hash : inflatedMerkleBlockMessage.getPartialMerkleTree().getHashes()) {
            Assert.assertEquals(hash, merkleBlockMessage.getPartialMerkleTree().getHashes().get(i));
            i += 1;
        }

        Assert.assertEquals(MutableByteArray.wrap(HexUtil.hexStringToByteArray(merkleBlockBytesString)), merkleBlockMessage.getBytes());
    }
}
