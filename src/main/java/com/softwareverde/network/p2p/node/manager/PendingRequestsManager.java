package com.softwareverde.network.p2p.node.manager;

import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.util.type.time.SystemTime;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PendingRequestsManager<NODE> extends SleepyService {
    public static final Long TIMEOUT_MS = 30000L;
    public static final Long POLL_TIME_MS = 1000L;

    protected final ThreadPool _threadPool = new ThreadPool(0, 4, POLL_TIME_MS);

    protected final SystemTime _systemTime;
    protected final ConcurrentHashMap<NodeManager.NodeApiRequest<NODE>, Long> _pendingRequests = new ConcurrentHashMap<NodeManager.NodeApiRequest<NODE>, Long>();

    public PendingRequestsManager(final SystemTime systemTime) {
        _systemTime = systemTime;
    }

    public void addPendingRequest(final NodeManager.NodeApiRequest<NODE> apiRequest) {
        final Long now = _systemTime.getCurrentTimeInMilliSeconds();
        _pendingRequests.put(apiRequest, now);
    }

    public Boolean removePendingRequest(final NodeManager.NodeApiRequest<NODE> apiRequest) {
        final Long startTime = _pendingRequests.remove(apiRequest);
        return (startTime == null);
    }

    @Override
    protected void _onStart() { }

    @Override
    protected Boolean _run() {
        try { Thread.sleep(POLL_TIME_MS); }
        catch (final InterruptedException exception) { return false; }

        final Long now = _systemTime.getCurrentTimeInMilliSeconds();
        final Long expiredStartTime = (now - TIMEOUT_MS);

        final Iterator<Map.Entry<NodeManager.NodeApiRequest<NODE>, Long>> iterator = _pendingRequests.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<NodeManager.NodeApiRequest<NODE>, Long> pair = iterator.next();
            final NodeManager.NodeApiRequest<NODE> apiRequest = pair.getKey();
            final Long requestTime = pair.getValue();

            if (apiRequest == null) { continue; }
            if (requestTime == null) { continue; }

            if (requestTime < expiredStartTime) {
                apiRequest.didTimeout = true;

                _threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        apiRequest.onFailure();
                    }
                });

                iterator.remove();
            }
        }

        return (! _pendingRequests.isEmpty());
    }

    @Override
    protected void _onSleep() {
        for (final Map.Entry<NodeManager.NodeApiRequest<NODE>, Long> pair : _pendingRequests.entrySet()) {
            final NodeManager.NodeApiRequest<NODE> apiRequest = pair.getKey();
            apiRequest.onFailure();
        }
        _pendingRequests.clear();
    }

    @Override
    public void stop() {
        super.stop();

        _threadPool.abortAll();
        _threadPool.waitUntilIdle();
    }
}
