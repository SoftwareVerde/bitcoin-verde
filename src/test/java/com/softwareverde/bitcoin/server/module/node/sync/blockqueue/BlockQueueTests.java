package com.softwareverde.bitcoin.server.module.node.sync.blockqueue;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

public class BlockQueueTests {

    @Test
    public void should_add_out_of_order_blocks_after_required_block_is_added() {
        // Setup
        final BlockInflater blockInflater = new BlockInflater();
        final BlockQueue blockQueue = new BlockQueue();

        final Block block0 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));
        final Block block2 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_2));
        final Block block3 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_3));
        final Block block4 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_4));
        final Block block5 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_5));

        blockQueue.addBlock(block0);

        // Action

        { // Add out of order blocks...
            blockQueue.addBlock(block3);
            blockQueue.addBlock(block5);
            blockQueue.addBlock(block2);
            Assert.assertEquals(1, blockQueue.getSize().intValue());
        }

        { // Add required block for block2 (this also enables block3)...
            blockQueue.addBlock(block1);
            Assert.assertEquals(4, blockQueue.getSize().intValue());
        }

        { // Add required block for block5...
            blockQueue.addBlock(block4);
            Assert.assertEquals(6, blockQueue.getSize().intValue());
        }

        // Assert
        Assert.assertEquals(block0.getHash(), blockQueue.getNextBlock().getHash());
        Assert.assertEquals(block1.getHash(), blockQueue.getNextBlock().getHash());
        Assert.assertEquals(block2.getHash(), blockQueue.getNextBlock().getHash());
        Assert.assertEquals(block3.getHash(), blockQueue.getNextBlock().getHash());
        Assert.assertEquals(block4.getHash(), blockQueue.getNextBlock().getHash());
        Assert.assertEquals(block5.getHash(), blockQueue.getNextBlock().getHash());
    }
}
