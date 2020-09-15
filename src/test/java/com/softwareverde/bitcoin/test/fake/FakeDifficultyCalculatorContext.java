package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.block.validator.difficulty.AsertReferenceBlock;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.DifficultyCalculatorContext;
import org.junit.Assert;

import java.util.HashMap;

public class FakeDifficultyCalculatorContext implements DifficultyCalculatorContext {
    protected final HashMap<Long, BlockHeader> _blockHeaders = new HashMap<Long, BlockHeader>();
    protected final HashMap<Long, ChainWork> _chainWorks = new HashMap<Long, ChainWork>();
    protected final HashMap<Long, MedianBlockTime> _medianBlockTimes = new HashMap<Long, MedianBlockTime>();

    protected final AsertReferenceBlock _asertReferenceBlock;

    public FakeDifficultyCalculatorContext() {
        this(null);
    }

    public FakeDifficultyCalculatorContext(final AsertReferenceBlock asertReferenceBlock) {
        _asertReferenceBlock = asertReferenceBlock;
    }

    @Override
    public BlockHeader getBlockHeader(final Long blockHeight) {
        if (! _blockHeaders.containsKey(blockHeight)) {
            Assert.fail("Requesting unregistered BlockHeader for blockHeight: " + blockHeight);
        }
        return _blockHeaders.get(blockHeight);
    }

    @Override
    public ChainWork getChainWork(final Long blockHeight) {
        if (! _chainWorks.containsKey(blockHeight)) {
            Assert.fail("Requesting unregistered ChainWork for blockHeight: " + blockHeight);
        }
        return _chainWorks.get(blockHeight);
    }

    @Override
    public MedianBlockTime getMedianBlockTime(final Long blockHeight) {
        if (! _medianBlockTimes.containsKey(blockHeight)) {
            Assert.fail("Requesting unregistered MedianBlockTime for blockHeight: " + blockHeight);
        }
        return _medianBlockTimes.get(blockHeight);
    }

    public HashMap<Long, BlockHeader> getBlockHeaders() {
        return _blockHeaders;
    }

    public HashMap<Long, ChainWork> getChainWorks() {
        return _chainWorks;
    }

    public HashMap<Long, MedianBlockTime> getMedianBlockTimes() {
        return _medianBlockTimes;
    }

    @Override
    public AsertReferenceBlock getAsertReferenceBlock() {
        return _asertReferenceBlock;
    }

    @Override
    public DifficultyCalculator newDifficultyCalculator() {
        return new DifficultyCalculator(this);
    }
}
