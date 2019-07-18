package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.block.header.BlockHeader;

public class BatchedBlockHeaders {
    protected Long _startingBlockHeight = null;
    protected Long _endBlockHeight = 0L;

    protected final BlockHeader[] _blockHeaders;

    public BatchedBlockHeaders(final Integer blockHeaderCount) {
        _blockHeaders = new BlockHeader[blockHeaderCount];
    }

    public Long getStartingBlockHeight() {
        return _startingBlockHeight;
    }

    public Long getEndBlockHeight() {
        return _endBlockHeight;
    }

    /**
     * Stores the BlockHeader (and its blockHeight) for batch validation.
     *  Only ::MAX_BATCH_COUNT headers may be stored.
     *  Stored Headers must be sequential and in ascending order.
     */
    public void put(final Long blockHeight, final BlockHeader blockHeader) {
        if (_startingBlockHeight == null) {
            _startingBlockHeight = blockHeight;
        }

        if (blockHeight >= _endBlockHeight) {
            _endBlockHeight = blockHeight;
        }

        if (blockHeight < _startingBlockHeight) {
            throw new IndexOutOfBoundsException("Attempting to batch non-sequential headers. _startingBlockHeight=" + _startingBlockHeight + " blockHeight=" + blockHeight);
        }

        final int index = (int) (blockHeight - _startingBlockHeight);
        if (index >= _blockHeaders.length) {
            throw new IndexOutOfBoundsException("Attempting to batch extra sparse headers. _startingBlockHeight=" + _startingBlockHeight + " blockHeight=" + blockHeight);
        }

        _blockHeaders[index] = blockHeader;
    }

    /**
     * Returns the BlockHeader from the batch by its blockHeight.
     *  If the BlockHeight is not contained within this batch, null is returned.
     */
    public BlockHeader getBlockHeader(final Long blockHeight) {
        if (blockHeight < _startingBlockHeight) { return null; }
        if (blockHeight > (_startingBlockHeight + _blockHeaders.length)) { return null; }

        final int index = (int) (blockHeight - _startingBlockHeight);
        return _blockHeaders[index];
    }
}