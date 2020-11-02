package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;

import java.math.BigInteger;

public class AsertReferenceBlock {
    protected final BigInteger blockHeight;

    /**
     * parentBlockTime is the timestamp of the Anchor's parent block.
     *  It is not the MedianBlockTime (or the MedianTimePast) of the Anchor Block.
     *  parentBlockTime is in seconds.
     */
    protected final Long parentBlockTimestamp;

    protected final Difficulty difficulty;

    public AsertReferenceBlock(final Long blockHeight, final Long parentBlockTimestamp, final Difficulty difficulty) {
        this.blockHeight = BigInteger.valueOf(blockHeight);
        this.parentBlockTimestamp = parentBlockTimestamp;
        this.difficulty = difficulty;
    }

    public AsertReferenceBlock(final BigInteger blockHeight, final Long parentBlockTimestamp, final Difficulty difficulty) {
        this.blockHeight = blockHeight;
        this.parentBlockTimestamp = parentBlockTimestamp;
        this.difficulty = difficulty;
    }
}
