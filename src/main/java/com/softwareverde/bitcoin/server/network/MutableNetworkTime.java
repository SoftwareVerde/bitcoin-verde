package com.softwareverde.bitcoin.server.network;

import com.softwareverde.util.type.time.SystemTime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MutableNetworkTime implements NetworkTime {

    // NOTE: Due to InsertionSort's efficiency for resorting nearly-sorted lists, it is the implementation chosen to maintain the median network time.
    protected static void _insertionSort(final List<Long> list) {
        final int n = list.size();
        for (int i = 1; i < n; ++i) {
            final long keyValue = list.get(i);

            int j = i - 1;
            while (j >= 0 && list.get(j) > keyValue) {
                list.set((j + 1), list.get(j));
                j = (j - 1);
            }
            list.set((j + 1), keyValue);
        }
    }

    protected final ReentrantReadWriteLock.ReadLock _readLock;
    protected final ReentrantReadWriteLock.WriteLock _writeLock;

    protected final SystemTime _systemTime = new SystemTime();
    protected final List<Long> _networkTimeOffsets = new ArrayList<Long>();

    public MutableNetworkTime() {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        _readLock = readWriteLock.readLock();
        _writeLock = readWriteLock.writeLock();

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
            _insertionSort(_networkTimeOffsets);
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
