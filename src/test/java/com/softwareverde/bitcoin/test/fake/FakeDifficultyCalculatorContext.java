package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.block.validator.difficulty.AsertReferenceBlock;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.DifficultyCalculatorContext;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import org.junit.Assert;

import java.util.HashMap;
import java.util.HashSet;

public class FakeDifficultyCalculatorContext implements DifficultyCalculatorContext {
    protected final HashMap<Long, BlockHeader> _blockHeaders = new HashMap<Long, BlockHeader>();
    protected final HashMap<Long, ChainWork> _chainWorks = new HashMap<Long, ChainWork>();
    protected final HashMap<Long, MedianBlockTime> _medianBlockTimes = new HashMap<Long, MedianBlockTime>();
    protected final HashSet<BlockHeader> _requestedBlocks = new HashSet<BlockHeader>();

    protected final AsertReferenceBlock _asertReferenceBlock;
    protected final UpgradeSchedule _upgradeSchedule;

    public FakeDifficultyCalculatorContext(final UpgradeSchedule upgradeSchedule) {
        this(null, upgradeSchedule);

    }

    public FakeDifficultyCalculatorContext(final AsertReferenceBlock asertReferenceBlock, final UpgradeSchedule upgradeSchedule) {
        _asertReferenceBlock = asertReferenceBlock;
        _upgradeSchedule = upgradeSchedule;
    }

    @Override
    public BlockHeader getBlockHeader(final Long blockHeight) {
        if (! _blockHeaders.containsKey(blockHeight)) {
            Assert.fail("Requesting unregistered BlockHeader for blockHeight: " + blockHeight);
        }

        final BlockHeader blockHeader = _blockHeaders.get(blockHeight);
        _requestedBlocks.add(blockHeader);
        return blockHeader;
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

    @Override
    public AsertReferenceBlock getAsertReferenceBlock() {
        return _asertReferenceBlock;
    }

    @Override
    public DifficultyCalculator newDifficultyCalculator() {
        return new DifficultyCalculator(this);
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

    public List<BlockHeader> getUnusedBlocks() {
        final MutableList<BlockHeader> unusedBlocks = new MutableList<BlockHeader>();
        for (final BlockHeader blockHeader : _blockHeaders.values()) {
            if (! _requestedBlocks.contains(blockHeader)) {
                unusedBlocks.add(blockHeader);
            }
        }
        return unusedBlocks;
    }

    @Override
    public UpgradeSchedule getUpgradeSchedule() {
        return _upgradeSchedule;
    }
}
