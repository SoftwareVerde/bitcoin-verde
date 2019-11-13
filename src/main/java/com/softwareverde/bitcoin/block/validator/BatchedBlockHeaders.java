package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.work.BlockWork;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;

public class BatchedBlockHeaders {
    protected ChainWork _currentChainWork = null;
    protected Long _startingBlockHeight = null;
    protected Long _endBlockHeight = 0L;

    protected final BlockHeader[] _blockHeaders;
    protected final ChainWork[] _chainWorks;

    public BatchedBlockHeaders(final Integer blockHeaderCount) {
        _blockHeaders = new BlockHeader[blockHeaderCount];
        _chainWorks = new ChainWork[blockHeaderCount];
    }

    public Long getStartingBlockHeight() {
        return _startingBlockHeight;
    }

    public Long getEndBlockHeight() {
        return _endBlockHeight;
    }

    /**
     * Sets the ChainWork for the current state of the chain.
     *  The startingChainWork should be the ChainWork of the parent block of the first added BlockHeader.
     *  ::setCurrentChainWork should be invoked before the first call to ::put.
     */
    public void setCurrentChainWork(final ChainWork startingChainWork) {
        _currentChainWork = startingChainWork;
    }

    /**
     * Stores the BlockHeader (and its blockHeight) for batch validation.
     *  Only ::MAX_BATCH_COUNT headers may be stored.
     *  Stored Headers must be sequential and in ascending order.
     *  If CurrentChainWork is set, the ChainWork for the blockHeader will be calculated and cached.
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

        if (_currentChainWork != null) {
            final Difficulty difficulty = blockHeader.getDifficulty();
            final BlockWork blockWork = difficulty.calculateWork();
            _currentChainWork = ChainWork.add(_currentChainWork, blockWork);
            _chainWorks[index] = _currentChainWork;
        }
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

    public ChainWork getChainWork(final Long blockHeight) {
        if (blockHeight < _startingBlockHeight) { return null; }
        if (blockHeight > (_startingBlockHeight + _blockHeaders.length)) { return null; }

        final int index = (int) (blockHeight - _startingBlockHeight);
        return _chainWorks[index];
    }
}