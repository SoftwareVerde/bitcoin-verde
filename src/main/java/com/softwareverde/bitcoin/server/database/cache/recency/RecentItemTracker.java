package com.softwareverde.bitcoin.server.database.cache.recency;

import com.softwareverde.async.ConcurrentHashSet;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class RecentItemTracker<T> {
    protected final ConcurrentHashMap<T, RecentItem<T>> _recentItemInstances;
    protected final ConcurrentLinkedDeque<RecentItem<T>> _recentItems;
    protected final ConcurrentHashSet<Long> _skippedAccesses;
    protected long _oldestAccess = Long.MIN_VALUE;
    protected long _nextAccess = Long.MIN_VALUE;

    protected double _msSpentSearching = 0D;

    protected void _resetDebug() {
        _msSpentSearching = 0D;
    }

    public RecentItemTracker(final Integer initialCapacity) {
        _recentItemInstances = new ConcurrentHashMap<T, RecentItem<T>>(initialCapacity);
        _recentItems = new ConcurrentLinkedDeque<RecentItem<T>>();
        _skippedAccesses = new ConcurrentHashSet<Long>();
    }

    public void markRecent(final T item) {
        final NanoTimer timer = new NanoTimer();
        timer.start();

        final RecentItem<T> recentItem;
        {
            final RecentItem<T> existingInstance = _recentItemInstances.get(item);
            if (existingInstance != null) {
                recentItem = existingInstance;
            }
            else {
                recentItem = new RecentItem<T>(item);
                _recentItemInstances.put(item, recentItem);
            }
        }

        if (recentItem.lastAccess != null) {
            _skippedAccesses.add(recentItem.lastAccess);
        }

        recentItem.lastAccess = _nextAccess;
        _recentItems.addLast(recentItem);

        _nextAccess += 1L;

        timer.stop();
        _msSpentSearching += timer.getMillisecondsElapsed();
    }

    public T getOldestItem() {
        final NanoTimer timer = new NanoTimer();
        timer.start();

        while (_skippedAccesses.remove(_oldestAccess)) {
            _oldestAccess += 1L;
        }

        while (! _recentItems.isEmpty()) {
            final RecentItem<T> recentItem = _recentItems.removeFirst();
            if (Util.areEqual(recentItem.lastAccess, _oldestAccess)) {
                final T item = recentItem.item;
                _recentItemInstances.remove(item);
                _oldestAccess += 1;

                timer.stop();
                _msSpentSearching += timer.getMillisecondsElapsed();

                return item;
            }
        }

        timer.stop();
        _msSpentSearching += timer.getMillisecondsElapsed();

        return null;
    }

    public Integer getSize() {
        return _recentItems.size();
    }

    public Double getMsSpentSearching() {
        return _msSpentSearching;
    }

    public void clear() {
        _recentItemInstances.clear();
        _recentItems.clear();
        _skippedAccesses.clear();
        _oldestAccess = Long.MIN_VALUE;
        _nextAccess = Long.MIN_VALUE;

        _resetDebug();
    }

    public void resetDebug() {
        _resetDebug();
    }
}
