package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.IoUtil;
import org.junit.Assert;
import org.junit.Test;

public class BlockInflaterTests extends UnitTest {
    @Test
    public void should_inflate_valid_block() {
        // Setup
        final BlockInflater blockInflater = new BlockInflater();
        final BlockDeflater blockDeflater = new BlockDeflater();

        final Sha256Hash expectedHash = Sha256Hash.fromHexString("00000000AFE94C578B4DC327AA64E1203283C5FD5F152CE886341766298CF523");
        final ByteArray blockBytes = ByteArray.fromHexString(IoUtil.getResource("/blocks/00000000AFE94C578B4DC327AA64E1203283C5FD5F152CE886341766298CF523"));

        // Action
        final Block block = blockInflater.fromBytes(blockBytes);
        final ByteArray deflatedBlock = blockDeflater.toBytes(block);

        // Assert
        Assert.assertEquals(expectedHash, block.getHash());
        Assert.assertEquals(blockBytes, deflatedBlock);
    }
}
