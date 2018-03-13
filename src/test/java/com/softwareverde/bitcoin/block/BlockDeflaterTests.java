package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.bitcoin.type.bytearray.ByteArray;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.util.IoUtil;
import org.junit.Test;

public class BlockDeflaterTests {

    @Test
    public void bug_0001_should_deflate_inflated_block_29664() {
        // Setup
        final BlockInflater blockInflater = new BlockInflater();
        final BlockDeflater blockDeflater = new BlockDeflater();

        final byte[] expectedBytes = BitcoinUtil.hexStringToByteArray(IoUtil.getResource("/blocks/00000000AFE94C578B4DC327AA64E1203283C5FD5F152CE886341766298CF523"));
        final Block block = blockInflater.fromBytes(expectedBytes);

        // Action
        final ByteArray blockBytes = blockDeflater.toBytes(block);

        // Assert
        TestUtil.assertEqual(expectedBytes, blockBytes.getBytes());
    }
}
