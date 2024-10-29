package com.softwareverde.bitcoin.test.util;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;

public class BlockTestUtil {
    /**
     * FakeMutableBlock does not require a proper Hash to be valid.
     *  This modification allows the block to be valid without the required amount of Proof of Work.
     */
    public static class FakeMutableBlock extends MutableBlock {
        @Override
        public Boolean isValid() { return true; }
    }

    public static FakeMutableBlock createBlock() {
        final FakeMutableBlock mutableBlock = new FakeMutableBlock();

        mutableBlock.setDifficulty(Difficulty.BASE_DIFFICULTY);
        mutableBlock.setNonce(0L);
        mutableBlock.setTimestamp(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP);
        mutableBlock.setVersion(Block.VERSION);

        return mutableBlock;
    }

    protected BlockTestUtil() { }
}
