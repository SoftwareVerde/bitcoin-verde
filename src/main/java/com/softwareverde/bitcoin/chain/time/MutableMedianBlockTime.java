package com.softwareverde.bitcoin.chain.time;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.util.RotatingQueue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MutableMedianBlockTime implements MedianBlockTime {
    protected final ReentrantReadWriteLock.ReadLock _readLock;
    protected final ReentrantReadWriteLock.WriteLock _writeLock;
    protected final RotatingQueue<BlockHeader> _previousBlocks;

    protected Long _getMedianBlockTimeInMilliseconds() {
        final Integer blockCount = _previousBlocks.size();

        if (blockCount < BLOCK_COUNT) {
            // Logger.log("NOTICE: Attempted to retrieve MedianBlockTime without setting at least " + BLOCK_COUNT + " blocks.");
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

    public MutableMedianBlockTime() {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        _readLock = readWriteLock.readLock();
        _writeLock = readWriteLock.writeLock();

        _previousBlocks = new RotatingQueue<BlockHeader>(BLOCK_COUNT);
    }

    public void addBlock(final BlockHeader blockHeader) {
        try {
            _writeLock.lock();
            _previousBlocks.add(blockHeader);
        }
        finally {
            _writeLock.unlock();
        }
    }

    public Boolean hasRequiredBlockCount() {
        final Boolean hasRequiredBlockCount;
        try {
            _readLock.lock();
            hasRequiredBlockCount = (_previousBlocks.size() >= BLOCK_COUNT);
        }
        finally {
            _readLock.unlock();
        }

        return hasRequiredBlockCount;
    }

    @Override
    public Long getCurrentTimeInSeconds() {
        final Long medianBlockTime;
        try {
            _readLock.lock();
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
        try {
            _readLock.lock();
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
