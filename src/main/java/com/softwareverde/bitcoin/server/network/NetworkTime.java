package com.softwareverde.bitcoin.server.network;

import com.softwareverde.util.type.time.SystemTime;
import com.softwareverde.util.type.time.Time;

import java.util.ArrayList;
import java.util.List;

public class NetworkTime implements Time {

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

    protected final SystemTime _systemTime = new SystemTime();
    protected final List<Long> _networkTimeOffsets = new ArrayList<Long>();

    public NetworkTime() {
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
        final Long networkTimeInMilliseconds = _getMedianNetworkTimeInMilliseconds();
        return (networkTimeInMilliseconds / 1000L);
    }

    @Override
    public Long getCurrentTimeInMilliSeconds() {
        return _getMedianNetworkTimeInMilliseconds();
    }

    public void includeOffsetInSeconds(final Long networkTimeOffset) {
        synchronized (_networkTimeOffsets) {
            _networkTimeOffsets.add(networkTimeOffset);
            _insertionSort(_networkTimeOffsets);
        }
    }
}
