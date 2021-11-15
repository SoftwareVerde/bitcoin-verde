package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.util.type.time.SystemTime;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RequestBabysitter extends SleepyService {
    public static abstract class WatchedRequest {
        private final AtomicBoolean _watchHasCompleted = new AtomicBoolean(false);

        protected final Boolean onWatchEnded() {
            return _watchHasCompleted.compareAndSet(false, true);
        }

        protected final Boolean watchHasEnded() {
            return (! _watchHasCompleted.compareAndSet(false, true));
        }

        protected abstract void onExpiration();
    }

    protected final SystemTime _systemTime = new SystemTime();
    protected final TreeMap<Long, List<WatchedRequest>> _requests = new TreeMap<>();

    protected final ReentrantReadWriteLock.ReadLock _readLock;
    protected final ReentrantReadWriteLock.WriteLock _writeLock;

    @Override
    protected void _onStart() { }

    @Override
    protected Boolean _run() {
        final List<WatchedRequest> removedWatchedRequests;
        final List<Long> itemsToRemove = new ArrayList<>();

        _readLock.lock();
        try {
            final Long now = _systemTime.getCurrentTimeInSeconds();

            for (final Long expirationTime : _requests.keySet()) {
                if (expirationTime > now) { break; }

                itemsToRemove.add(expirationTime);
            }
        }
        finally {
            _readLock.unlock();
        }

        if (! itemsToRemove.isEmpty()) {
            removedWatchedRequests = new ArrayList<>();
            _writeLock.lock();
            try {
                for (final Long expirationTime : itemsToRemove) {
                    final List<WatchedRequest> watchedRequests = _requests.remove(expirationTime);
                    if (watchedRequests == null) { continue; }

                    removedWatchedRequests.addAll(watchedRequests);
                }
            }
            finally {
                _writeLock.unlock();
            }
        }
        else {
            removedWatchedRequests = null;
        }

        if (removedWatchedRequests != null) {
            for (final WatchedRequest watchedRequest : removedWatchedRequests) {
                if (! watchedRequest.watchHasEnded()) {
                    watchedRequest.onExpiration();
                }
            }
        }

        try {
            Thread.sleep(1000L);
        }
        catch (final InterruptedException exception) {
            final Thread currentThread = Thread.currentThread();
            currentThread.interrupt();
            return false;
        }

        _readLock.lock();
        try {
            return (! _requests.isEmpty());
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    protected void _onSleep() { }

    public RequestBabysitter() {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);
        _readLock = readWriteLock.readLock();
        _writeLock = readWriteLock.writeLock();
    }

    public void watch(final WatchedRequest watchedRequest, final Long timeoutSeconds) {
        final long expireAfter = (_systemTime.getCurrentTimeInSeconds() + timeoutSeconds);

        List<WatchedRequest> list;
        _readLock.lock();
        try {
            list = _requests.get(expireAfter);
            if (list != null) {
                list.add(watchedRequest);
            }
        }
        finally {
            _readLock.unlock();
        }

        if (list == null) {
            _writeLock.lock();
            try {
                list = _requests.get(expireAfter);
                if (list == null) {
                    list = new ArrayList<>();
                    _requests.put(expireAfter, list);
                }
                list.add(watchedRequest);
            }
            finally {
                _writeLock.unlock();
            }
        }


        this.wakeUp();
    }
}
