package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.block.validator.difficulty.AsertReferenceBlock;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.DifficultyCalculatorContext;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.constable.map.mutable.MutableMap;
import com.softwareverde.constable.set.mutable.MutableHashSet;
import com.softwareverde.constable.set.mutable.MutableSet;
import org.junit.Assert;

public class FakeDifficultyCalculatorContext implements DifficultyCalculatorContext {
    protected final MutableMap<Long, BlockHeader> _blockHeaders = new MutableHashMap<>();
    protected final MutableMap<Long, ChainWork> _chainWorks = new MutableHashMap<>();
    protected final MutableMap<Long, MedianBlockTime> _medianBlockTimes = new MutableHashMap<>();
    protected final MutableSet<BlockHeader> _requestedBlocks = new MutableHashSet<>();

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


    public MutableMap<Long, BlockHeader> getBlockHeaders() {
        return _blockHeaders;
    }

    public MutableMap<Long, ChainWork> getChainWorks() {
        return _chainWorks;
    }

    public MutableMap<Long, MedianBlockTime> getMedianBlockTimes() {
        return _medianBlockTimes;
    }

    public List<BlockHeader> getUnusedBlocks() {
        final MutableList<BlockHeader> unusedBlocks = new MutableArrayList<>();
        for (final BlockHeader blockHeader : _blockHeaders.getValues()) {
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
