package com.softwareverde.bitcoin.chain.time;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.module.node.Blockchain;
import com.softwareverde.constable.Constable;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.Time;

public interface MedianBlockTime extends Time, Constable<ImmutableMedianBlockTime> {
    Integer BLOCK_COUNT = 11;
    Long GENESIS_BLOCK_TIMESTAMP = BitcoinConstants.getGenesisBlockTimestamp(); // In seconds.
    ImmutableMedianBlockTime MAX_VALUE = new ImmutableMedianBlockTime(Long.MAX_VALUE);

    static MedianBlockTime fromSeconds(final Long medianBlockTimeInSeconds) {
        return ImmutableMedianBlockTime.fromSeconds(medianBlockTimeInSeconds);
    }

    static MedianBlockTime fromMilliseconds(final Long medianBlockTimeInMilliseconds) {
        return ImmutableMedianBlockTime.fromMilliseconds(medianBlockTimeInMilliseconds);
    }

    interface BlockStore {
        BlockHeader getBlock(Long blockHeight);
    }

    /**
     * Calculates the MedianBlockTime for the provided Block at blockHeight.
     *  Blockchain must provide the BlockHeaders for the Block at blockHeight and its (MedianBlockTime.BLOCK_COUNT - 1) ancestors.
     */
    static MedianBlockTime calculateMedianBlockTime(final Long blockHeight, final Blockchain blockchain) {
        final MutableList<BlockHeader> blockHeadersInDescendingOrder = new MutableArrayList<>(MedianBlockTime.BLOCK_COUNT);

        // Load the blocks from the store in descending order, including the block at blockHeight...
        for (int i = 0; i < MedianBlockTime.BLOCK_COUNT; ++i) {
            final long blockIndex = (blockHeight - i);
            if (blockIndex < 0L) { break; }

            final BlockHeader block = blockchain.getBlockHeader(blockIndex);
            blockHeadersInDescendingOrder.add(block);
        }

        return MedianBlockTime.calculateMedianBlockTime(blockHeadersInDescendingOrder);
    }

    /**
     * Calculates the MedianBlockTime for the provided Block at blockHeight.
     *  The BlockHeader List have MedianBlockTime.BLOCK_COUNT items and must be in descending order.
     *  The returned MedianBlockTime will be the MedianBlockTime for the first BlockHeader within the List.
     */
    static MedianBlockTime calculateMedianBlockTime(final List<BlockHeader> blockHeadersInDescendingOrder) {
        if (blockHeadersInDescendingOrder.getCount() != 3) { return null; }

        final MutableMedianBlockTime medianBlockTime = new MutableMedianBlockTime();
        // Add the blocks to the MedianBlockTime in ascending order (lowest block-height is added first)...
        final int blockHeaderCount = blockHeadersInDescendingOrder.getCount();
        for (int i = 0; i < blockHeaderCount; ++i) {
            final BlockHeader blockHeader = blockHeadersInDescendingOrder.get(blockHeaderCount - i - 1);
            medianBlockTime.addBlock(blockHeader);
        }

        return medianBlockTime;
    }

    @Override
    ImmutableMedianBlockTime asConst();
}

abstract class MedianBlockTimeCore implements MedianBlockTime {
    @Override
    public String toString() {
        final Long currentTimeInSeconds = this.getCurrentTimeInSeconds();
        return Util.coalesce(currentTimeInSeconds).toString();
    }

    @Override
    public int hashCode() {
        final Long timeInSeconds = this.getCurrentTimeInSeconds();
        if (timeInSeconds == null) { return 0; }

        return timeInSeconds.hashCode();
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof MedianBlockTime)) { return false; }

        final MedianBlockTime medianBlockTime = (MedianBlockTime) object;

        final Long timeInSeconds0 = this.getCurrentTimeInSeconds();
        final Long timeInSeconds1 = medianBlockTime.getCurrentTimeInSeconds();

        return Util.areEqual(timeInSeconds0, timeInSeconds1);
    }
}
