package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

public class BlockTests {
    @Test
    public void block_should_be_invalid_if_merkle_mismatch() {
        // Setup
        final BlockInflater blockInflater = new BlockInflater();
        final BlockDeflater blockDeflater = new BlockDeflater();

        final Transaction transaction;
        {
            final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_2));
            transaction = block.getCoinbaseTransaction();
        }

        final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));
        Assert.assertTrue(block.isValid());

        final MutableBlock mutableBlock = new MutableBlock(block, block.getTransactions());
        Assert.assertTrue(mutableBlock.isValid());

        // Action
        mutableBlock.addTransaction(transaction);
        final ByteArray blockBytes = blockDeflater.toBytes(mutableBlock);
        final Block reinflatedModifiedBlock = blockInflater.fromBytes(blockBytes);

        // Assert
        Assert.assertFalse(mutableBlock.isValid());
        Assert.assertFalse(mutableBlock.asConst().isValid());
        Assert.assertFalse(reinflatedModifiedBlock.isValid());
        Assert.assertFalse(reinflatedModifiedBlock.asConst().isValid());
    }
}
