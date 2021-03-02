package com.softwareverde.bloomfilter;

import com.softwareverde.concurrent.lock.IndexLock;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.timer.MilliTimer;
import org.junit.Assert;
import org.junit.Test;

public class MutableBloomFilterTests {
    protected static boolean isLocked(final IndexLock indexLock, final int index) {
        final MilliTimer milliTimer = new MilliTimer();
        milliTimer.start();
        try {
            indexLock.lock(index);
        }
        finally {
            indexLock.unlock(index);
        }
        milliTimer.stop();
        return (milliTimer.getMillisecondsElapsed() > 5);
    }

    @Test
    public void should_release_all_locks_after_clear() throws Exception {
        // Setup
        final MutableBloomFilter mutableBloomFilter = new MutableBloomFilter(new MutableByteArray(512), 1, 0L);
        final int indexLockCount = mutableBloomFilter._indexLockSegmentCount;
        final IndexLock indexLock = mutableBloomFilter._indexLock;
        for (int i = 0; i < indexLockCount; ++i) {
            Assert.assertFalse(MutableBloomFilterTests.isLocked(indexLock, i));
        }

        // Action
        final Thread thread = (new Thread(new Runnable() {
            @Override
            public void run() {
                mutableBloomFilter.clear();
            }
        }));
        thread.start();
        thread.join(5000L);

        // Assert
        for (int i = 0; i < indexLockCount; ++i) {
            Assert.assertFalse(MutableBloomFilterTests.isLocked(indexLock, i));
        }
    }
}
