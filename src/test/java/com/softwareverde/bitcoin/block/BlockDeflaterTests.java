package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.IoUtil;
import org.junit.Assert;
import org.junit.Test;

public class BlockDeflaterTests {

    @Test
    public void should_deflate_inflated_block_29664() {
        // Setup
        final BlockInflater blockInflater = new BlockInflater();
        final BlockDeflater blockDeflater = new BlockDeflater();

        final byte[] expectedBytes = HexUtil.hexStringToByteArray(IoUtil.getResource("/blocks/00000000AFE94C578B4DC327AA64E1203283C5FD5F152CE886341766298CF523"));
        final Block block = blockInflater.fromBytes(expectedBytes);

        // Action
        final ByteArray blockBytes = blockDeflater.toBytes(block);

        // Assert
        TestUtil.assertEqual(expectedBytes, blockBytes.getBytes());
    }

    @Test
    public void should_calculate_correct_byte_count_for_block() {
        // Setup
        final BlockInflater blockInflater = new BlockInflater();
        final BlockDeflater blockDeflater = new BlockDeflater();

        final byte[] expectedBytes = HexUtil.hexStringToByteArray(IoUtil.getResource("/blocks/00000000000000000051CFB8C9B8191EC4EF14F8F44F3E2290D67A8A0A29DD05"));
        final Block block = blockInflater.fromBytes(expectedBytes);

        final Integer expectedByteCount = 999150;

        // Action
        final Integer toBytesByteCount = blockDeflater.toBytes(block).getByteCount();
        final Integer getByteCountByteCount = blockDeflater.getByteCount(block);

        // Assert
        Assert.assertEquals(expectedByteCount, toBytesByteCount);
        Assert.assertEquals(expectedByteCount, getByteCountByteCount);
    }
}
