package com.softwareverde.network.time;

import com.softwareverde.util.SortUtil;
import com.softwareverde.util.type.time.SystemTime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MutableNetworkTime implements NetworkTime, VolatileNetworkTime {
    public static MutableNetworkTime fromSystemTime(final SystemTime systemTime) {
        return new MutableNetworkTime(systemTime);
    }

    protected final ReentrantReadWriteLock.ReadLock _readLock;
    protected final ReentrantReadWriteLock.WriteLock _writeLock;

    protected final SystemTime _systemTime;
    protected final List<Long> _networkTimeOffsets;

    public MutableNetworkTime() {
        this(new SystemTime());
    }

    public MutableNetworkTime(final SystemTime systemTime) {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        _readLock = readWriteLock.readLock();
        _writeLock = readWriteLock.writeLock();

        _systemTime = systemTime;
        _networkTimeOffsets  = new ArrayList<>(16);

        _networkTimeOffsets.add(0L);
    }

    protected Long _getMedianNetworkTimeInMilliseconds() {
        final int offsetCount = _networkTimeOffsets.size();
        final Long selectedOffset = _networkTimeOffsets.get(offsetCount / 2);

        final Long now = _systemTime.getCurrentTimeInMilliSeconds();
        return (now + selectedOffset);
    }

    @Override
    public Long getCurrentTimeInSeconds() {
        final Long networkTimeInMilliseconds;
        try {
            _readLock.lock();
            networkTimeInMilliseconds = _getMedianNetworkTimeInMilliseconds();
        }
        finally {
            _readLock.unlock();
        }

        return (networkTimeInMilliseconds / 1000L);
    }

    @Override
    public Long getCurrentTimeInMilliSeconds() {
        final Long networkTimeInMilliseconds;
        try {
            _readLock.lock();
            networkTimeInMilliseconds = _getMedianNetworkTimeInMilliseconds();
        }
        finally {
            _readLock.unlock();
        }

        return networkTimeInMilliseconds;
    }

    public void includeOffsetInSeconds(final Long networkTimeOffset) {
        try {
            _writeLock.lock();
            _networkTimeOffsets.add(networkTimeOffset);
            SortUtil.insertionSort(_networkTimeOffsets, SortUtil.longComparator);
        }
        finally {
            _writeLock.unlock();
        }
    }

    @Override
    public ImmutableNetworkTime asConst() {
        return new ImmutableNetworkTime(this);
    }
}
