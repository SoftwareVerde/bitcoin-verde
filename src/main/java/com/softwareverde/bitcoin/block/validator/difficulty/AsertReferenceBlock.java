package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;

import java.math.BigInteger;

public class AsertReferenceBlock {
    protected final BigInteger blockHeight;
    protected final MedianBlockTime blockTime;
    protected final Difficulty difficulty;

    public AsertReferenceBlock(final Long blockHeight, final MedianBlockTime blockTime, final Difficulty difficulty) {
        this.blockHeight = BigInteger.valueOf(blockHeight);
        this.blockTime = blockTime;
        this.difficulty = difficulty;
    }

    public AsertReferenceBlock(final BigInteger blockHeight, final MedianBlockTime blockTime, final Difficulty difficulty) {
        this.blockHeight = blockHeight;
        this.blockTime = blockTime;
        this.difficulty = difficulty;
    }
}
