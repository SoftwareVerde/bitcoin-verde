package com.softwareverde.bitcoin.chain.time;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.util.RotatingQueue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MutableMedianBlockTime extends MedianBlockTimeCore implements MedianBlockTime, MedianBlockTimeWithBlocks, VolatileMedianBlockTime {
    protected final Integer _requiredBlockCount;
    protected final ReentrantReadWriteLock.ReadLock _readLock;
    protected final ReentrantReadWriteLock.WriteLock _writeLock;
    protected final RotatingQueue<BlockHeader> _previousBlocks;

    protected Long _getMedianBlockTimeInMilliseconds() {
        final int blockCount = _previousBlocks.size();

        if (blockCount < _requiredBlockCount) {
            // Logger.warn("NOTICE: Attempted to retrieve MedianBlockTime without setting at least " + _requiredBlockCount + " blocks.");
            return MedianBlockTime.GENESIS_BLOCK_TIMESTAMP;
        }

        final List<Long> blockTimestamps = new ArrayList<Long>(blockCount);
        for (final BlockHeader block : _previousBlocks) {
            blockTimestamps.add(block.getTimestamp());
        }
        Collections.sort(blockTimestamps);

        final int index = (blockCount / 2); // Typically the 6th block (index 5) of 11...
        return (blockTimestamps.get(index) * 1000L);
    }

    protected MutableMedianBlockTime(final Integer requiredBlockCount) {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        _readLock = readWriteLock.readLock();
        _writeLock = readWriteLock.writeLock();

        _requiredBlockCount = requiredBlockCount;
        _previousBlocks = new RotatingQueue<BlockHeader>(_requiredBlockCount);
    }

    public MutableMedianBlockTime() {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        _readLock = readWriteLock.readLock();
        _writeLock = readWriteLock.writeLock();

        _requiredBlockCount = BLOCK_COUNT;
        _previousBlocks = new RotatingQueue<BlockHeader>(_requiredBlockCount);
    }

    public void addBlock(final BlockHeader blockHeader) {
        _writeLock.lock();
        try {
            _previousBlocks.add(blockHeader);
        }
        finally {
            _writeLock.unlock();
        }
    }

    public Boolean hasRequiredBlockCount() {
        final boolean hasRequiredBlockCount;
        _readLock.lock();
        try {
            hasRequiredBlockCount = (_previousBlocks.size() >= _requiredBlockCount);
        }
        finally {
            _readLock.unlock();
        }

        return hasRequiredBlockCount;
    }

    public void clear() {
        _writeLock.lock();

        try {
            _previousBlocks.clear();
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void setTo(final MedianBlockTimeWithBlocks medianBlockTime) {
        _writeLock.lock();

        try {
            _previousBlocks.clear();

            if (medianBlockTime == null) { return; }
            for (int i = 0; i < _requiredBlockCount; ++i) {
                final int index = (_requiredBlockCount - i - 1);
                final BlockHeader blockHeader = medianBlockTime.getBlockHeader(index);
                if (blockHeader == null) { break; }

                _previousBlocks.add(blockHeader);
            }
        }
        finally {
            _writeLock.unlock();
        }
    }

    @Override
    public MedianBlockTime subset(final Integer blockCount) {
        final MutableMedianBlockTime medianBlockTime = new MutableMedianBlockTime(blockCount);

        final java.util.List<BlockHeader> blockHeaders = new java.util.ArrayList<BlockHeader>(_previousBlocks.size());
        blockHeaders.addAll(_previousBlocks);

        for (int i = 0; i < blockCount; ++i) {
            if (i >= blockHeaders.size()) { break; }
            final BlockHeader blockHeader = blockHeaders.get(blockHeaders.size() - i - 1);
            medianBlockTime.addBlock(blockHeader);
        }

        return medianBlockTime;
    }

    @Override
    public BlockHeader getBlockHeader(final Integer indexFromTip) {
        final java.util.List<BlockHeader> blockHeaders = new java.util.ArrayList<BlockHeader>(_previousBlocks.size());
        blockHeaders.addAll(_previousBlocks);
        return blockHeaders.get(blockHeaders.size() - indexFromTip - 1);
    }

    @Override
    public Long getCurrentTimeInSeconds() {
        final Long medianBlockTime;
        _readLock.lock();
        try {
            medianBlockTime = _getMedianBlockTimeInMilliseconds();
        }
        finally {
            _readLock.unlock();
        }

        return (medianBlockTime / 1000L);
    }

    @Override
    public Long getCurrentTimeInMilliSeconds() {
        final Long medianBlockTime;
        _readLock.lock();
        try {
            medianBlockTime = _getMedianBlockTimeInMilliseconds();
        }
        finally {
            _readLock.unlock();
        }

        return medianBlockTime;
    }

    @Override
    public ImmutableMedianBlockTime asConst() {
        return new ImmutableMedianBlockTime(this);
    }
}
