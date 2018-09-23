package com.softwareverde.network.p2p.node.manager.health;

import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.util.RotatingQueue;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;
import com.softwareverde.util.type.time.Time;

import java.util.Comparator;

public class NodeHealth {
    public static final Long FULL_HEALTH = 24L * 60L * 60L * 1000L;
    protected static final Float REGEN_TO_REQUEST_TIME_RATIO = 0.5F;

    public static final Comparator<NodeHealth> COMPARATOR = new Comparator<NodeHealth>() {
        @Override
        public int compare(final NodeHealth nodeHealth0, final NodeHealth nodeHealth1) {
            final Long nodeHealth0Value = nodeHealth0.calculateHealth();
            final Long nodeHealth1Value = nodeHealth1.calculateHealth();
            return (nodeHealth0Value.compareTo(nodeHealth1Value));
        }
    };

    public static class Request {
        protected static final Object NEXT_ID_MUTEX = new Object();
        protected static Long nextId = 1L;

        public final Long id;
        protected final Long _startTimeInMilliseconds;
        protected Long _endTimeInMilliseconds;

        protected Request(final Long startTimeInMilliseconds) {
            synchronized (NEXT_ID_MUTEX) {
                this.id = nextId;
                nextId += 1;
            }

            _startTimeInMilliseconds = startTimeInMilliseconds;
        }

        protected void setEndTimeInMilliseconds(final Long endTimeInMilliseconds) {
            _endTimeInMilliseconds = endTimeInMilliseconds;
        }

        Long getEndTimeInMilliseconds() { return _endTimeInMilliseconds; }
        Long getStartTimeInMilliseconds() { return _startTimeInMilliseconds; }

        Long calculateDurationInMilliseconds() {
            if (_endTimeInMilliseconds == null) { return null; }
            return (_endTimeInMilliseconds - _startTimeInMilliseconds);
        }
    }

    protected final Long _maxHealth = FULL_HEALTH;

    protected final Object _mutex = new Object();
    protected final Time _systemTime;
    protected final NodeId _nodeId;
    protected RotatingQueue<Request> _requests = new RotatingQueue<Request>(1024);
    protected Long _pingInMilliseconds = 0L;

    protected MutableList<Request> _getRecentRequests() {
        final MutableList<Request> recentRequests = new MutableList<Request>(_requests.size());

        final Long now = _systemTime.getCurrentTimeInMilliSeconds();
        final Long millisecondsRequiredToHealToFullHealth = _maxHealth;
        final Long minimumAge = (now - millisecondsRequiredToHealToFullHealth);

        for (final Request request : _requests) {
            if (request.getEndTimeInMilliseconds() >= minimumAge) {
                recentRequests.add(request);
            }
        }

        return recentRequests;
    }

    public NodeHealth(final NodeId nodeId, final Time systemTime) {
        _systemTime = systemTime;
        _nodeId = nodeId;
    }

    public NodeHealth(final NodeId nodeId) {
        _systemTime = new SystemTime();
        _nodeId = nodeId;
    }

    public Request onMessageSent() {
        synchronized (_mutex) {
            final Long now = _systemTime.getCurrentTimeInMilliSeconds();
            final Request request = new Request(now);

            _requests.add(request);

            return request;
        }
    }

    public void onMessageReceived(final Request request) {
        if (request == null) { return; }

        synchronized (_mutex) {
            final Long now = _systemTime.getCurrentTimeInMilliSeconds();
            request.setEndTimeInMilliseconds(now);
        }
    }

    public void updatePingInMilliseconds(final Long ping) {
        _pingInMilliseconds = ping;
    }

    public Long calculateHealth() {
        synchronized (_mutex) {
            final Long maxHealth = (_maxHealth - _pingInMilliseconds);

            final Long now = _systemTime.getCurrentTimeInMilliSeconds();
            final Long millisecondsRequiredToHealToFullHealth = _maxHealth;
            final Long minimumAge = (now - millisecondsRequiredToHealToFullHealth);

            long health = maxHealth;
            Long previousRequestEndTime = null;
            for (final Request request : _requests) {
                if (Util.coalesce(request.getEndTimeInMilliseconds(), Long.MAX_VALUE) < minimumAge) { continue; }

                if (previousRequestEndTime != null) {
                    health = Math.min(maxHealth, health + (request.getStartTimeInMilliseconds() - previousRequestEndTime));
                }

                final Long consumedHealth;
                { // Consume the elapsed duration, or 5% of the max health if it has not been completed...
                    final Long requestDurationInMilliseconds = request.calculateDurationInMilliseconds();
                    if (requestDurationInMilliseconds == null) {
                        consumedHealth = maxHealth / 20L;
                    }
                    else {
                        consumedHealth = (long) (requestDurationInMilliseconds / REGEN_TO_REQUEST_TIME_RATIO);
                    }
                }

                health = Math.max(0, health - consumedHealth);
                previousRequestEndTime = request.getEndTimeInMilliseconds();
            }
            if (previousRequestEndTime != null) {
                health = Math.min(maxHealth, health + (now - previousRequestEndTime));
            }
            return health;
        }
    }

    public NodeId getNodeId() {
        return _nodeId;
    }
}
