package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.constable.util.ConstUtil;
import com.softwareverde.bitcoin.context.ContextException;
import com.softwareverde.bitcoin.context.core.AsertReferenceBlockLoader;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.constable.map.mutable.MutableMap;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

public class FakeReferenceBlockLoaderContext implements AsertReferenceBlockLoader.ReferenceBlockLoaderContext {
    protected final MutableMap<BlockchainSegmentId, BlockId> _headBlockIds = new MutableHashMap<>();
    protected final MutableMap<BlockId, MedianBlockTime> _medianBlockTimes = new MutableHashMap<>();
    protected final MutableMap<BlockId, Long> _blockTimestamps = new MutableHashMap<>();
    protected final MutableMap<BlockId, Long> _blockHeights = new MutableHashMap<>();
    protected final MutableMap<BlockId, Difficulty> _difficulties = new MutableHashMap<>();
    protected final UpgradeSchedule _upgradeSchedule;

    protected Integer _lookupCount = 0;
    protected Integer _medianTimePastCalculationCount = 0;

    public FakeReferenceBlockLoaderContext(final UpgradeSchedule upgradeSchedule) {
        _upgradeSchedule = upgradeSchedule;
    }

    public void setBlockHeader(final BlockchainSegmentId blockchainSegmentId, final BlockId blockId, final Long blockHeight, final MedianBlockTime medianBlockTime, final Long blockTimestamp, final Difficulty difficulty) {
        final BlockId currentHeadBlockId = _headBlockIds.get(blockchainSegmentId);
        final Long currentHeadBlockHeight = ((currentHeadBlockId != null) ? _blockHeights.get(currentHeadBlockId) : null);
        if (Util.coalesce(currentHeadBlockHeight, Long.MIN_VALUE) < blockHeight) {
            _headBlockIds.put(blockchainSegmentId, blockId);
        }

        _medianBlockTimes.put(blockId, ConstUtil.asConstOrNull(medianBlockTime));
        _blockHeights.put(blockId, blockHeight);
        _difficulties.put(blockId, difficulty);
        _blockTimestamps.put(blockId, blockTimestamp);
    }

    public Integer getLookupCount() {
        return _lookupCount;
    }

    public Integer getMedianTimePastCalculationCount() {
        return _medianTimePastCalculationCount;
    }

    @Override
    public BlockId getHeadBlockIdOfBlockchainSegment(final BlockchainSegmentId blockchainSegmentId) {
        final BlockId blockId =  _headBlockIds.get(blockchainSegmentId);

        if (blockId == null) {
            Logger.debug("Requested unknown BlockId for BlockchainSegmentId: " + blockchainSegmentId);
        }

        return blockId;
    }

    @Override
    public MedianBlockTime getMedianBlockTime(final BlockId blockId) {
        _medianTimePastCalculationCount += 1;

        final MedianBlockTime medianBlockTime = _medianBlockTimes.get(blockId);

        if (medianBlockTime == null) {
            Logger.debug("Requested unknown MedianBlockTime for BlockId: " + blockId);
        }

        return medianBlockTime;
    }

    @Override
    public Long getBlockTimestamp(final BlockId blockId) throws ContextException {
        final Long blockTimestamp = _blockTimestamps.get(blockId);
        if (blockTimestamp == null) {
            Logger.debug("Requested unknown Timestamp for BlockId: " + blockId);
        }

        return blockTimestamp;
    }

    @Override
    public Long getBlockHeight(final BlockId blockId) {
        _lookupCount += 1;

        final Long blockHeight = _blockHeights.get(blockId);

        if (blockHeight == null) {
            Logger.debug("Requested unknown BlockHeight for BlockId: " + blockId);
        }

        return blockHeight;
    }

    @Override
    public BlockId getBlockIdAtHeight(final BlockchainSegmentId blockchainSegmentId, final Long blockHeight) {
        _lookupCount += 1;

        for (final BlockId blockId : _blockHeights.getKeys()) {
            final Long blockIdBlockHeight = _blockHeights.get(blockId);
            if (Util.areEqual(blockHeight, blockIdBlockHeight)) {
                return blockId;
            }
        }

        Logger.debug("Requested unknown BlockId at Height: " + blockHeight);
        return null;
    }

    @Override
    public Difficulty getDifficulty(final BlockId blockId) {
        final Difficulty difficulty = _difficulties.get(blockId);

        if (difficulty == null) {
            Logger.debug("Requested unknown Difficulty for BlockId: " + blockId);
        }

        return difficulty;
    }

    @Override
    public UpgradeSchedule getUpgradeSchedule() {
        return _upgradeSchedule;
    }
}
