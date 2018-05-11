package com.softwareverde.network.p2p.node.manager.health;

import com.softwareverde.bitcoin.type.time.SystemTime;
import com.softwareverde.bitcoin.type.time.Time;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.util.RotatingQueue;

import java.util.Comparator;

public class NodeHealth {
    public static final Comparator<NodeHealth> COMPARATOR = new Comparator<NodeHealth>() {
        @Override
        public int compare(final NodeHealth nodeHealth0, final NodeHealth nodeHealth1) {
            final Integer nodeHealth0Value = nodeHealth0.calculateHealth();
            final Integer nodeHealth1Value = nodeHealth1.calculateHealth();
            return (nodeHealth0Value.compareTo(nodeHealth1Value));
        }
    };

    protected static class Request {
        public final Long timestampInMilliseconds;
        public final Boolean wasSuccessful;

        public Request(final Long timestampInMilliseconds, final Boolean wasSuccessful) {
            this.timestampInMilliseconds = timestampInMilliseconds;
            this.wasSuccessful = wasSuccessful;
        }
    }

    protected Integer _healthPerSecond = 1;
    protected Integer _healthConsumedPerRequest = 10;
    protected Integer _maxHealth = 100;

    protected final Object _mutex = new Object();
    protected final Time _systemTime;
    protected final NodeId _nodeId;
    protected Long _regenMsConsumed = 0L;
    protected Long _lastMessageTime = null;
    protected Float _healthRegenRemainder = 0F;
    protected Integer _health = _maxHealth;
    protected RotatingQueue<Request> _requests = new RotatingQueue<Request>(25);

    protected void _calculateHealthRegen() {
        if (_lastMessageTime == null) { return; }

        final long nowInMilliseconds = _systemTime.getCurrentTimeInMilliSeconds();
        final long msElapsedSinceLastMessage = Math.max(0L, (nowInMilliseconds - _lastMessageTime));

        final float recencyHealthRegenFloat = ( ( (msElapsedSinceLastMessage - _regenMsConsumed) / 1_000F) * _healthPerSecond) + _healthRegenRemainder;
        final int recencyHealthRegen = (int) (recencyHealthRegenFloat);

        _health = Math.min(_maxHealth, _health + recencyHealthRegen);
        _healthRegenRemainder = (recencyHealthRegenFloat - recencyHealthRegen);
        _regenMsConsumed = msElapsedSinceLastMessage;
    }

    public NodeHealth(final NodeId nodeId, final Time systemTime) {
        _systemTime = systemTime;
        _nodeId = nodeId;
    }

    public NodeHealth(final NodeId nodeId) {
        _systemTime = new SystemTime();
        _nodeId = nodeId;
    }

    public void setHealthPerSecond(final Integer healthPerSecond) {
        _healthPerSecond = healthPerSecond;
    }

    public void setHealthConsumedPerRequest(final Integer healthConsumedPerRequest) {
        _healthConsumedPerRequest = healthConsumedPerRequest;
    }

    public void setMaxHealth(final Integer maxHealth) {
        _maxHealth = maxHealth;
    }

    public void onMessageSent() {
        synchronized (_mutex) {
            _calculateHealthRegen();

            _lastMessageTime = _systemTime.getCurrentTimeInMilliSeconds();
            _regenMsConsumed = 0L;

            _health = Math.max(0, _health - _healthConsumedPerRequest);
        }
    }

    public void onMessageReceived(final Boolean wasSuccessful) {
        synchronized (_mutex) {
            _calculateHealthRegen();

            final Long now = _systemTime.getCurrentTimeInMilliSeconds();
            _requests.add(new Request(now, wasSuccessful));
        }
    }

    public Integer calculateHealth() {
        // TODO: Include ping into calculation...

        synchronized (_mutex) {
            _calculateHealthRegen();

            final long nowInMilliseconds = _systemTime.getCurrentTimeInMilliSeconds();
            final Integer messageFailureCount;
            final Integer totalMessageCount;
            {
                int recentFailureCount = 0;
                int recentMessageCount = 0;
                for (final Request request : _requests) {
                    final long requestAgeInMilliseconds = (nowInMilliseconds - request.timestampInMilliseconds);
                    if (requestAgeInMilliseconds < 60L * 1_000L) {
                        recentMessageCount += 1;

                        if (! request.wasSuccessful) {
                            recentFailureCount += 1;
                        }
                    }
                }
                messageFailureCount = recentFailureCount;
                totalMessageCount = recentMessageCount;
            }

            final float failureRate = (totalMessageCount > 0 ? (messageFailureCount.floatValue() / totalMessageCount) : 0.0F);
            return (int) (_health * (1.0F - failureRate));
        }
    }

    public NodeId getNodeId() {
        return _nodeId;
    }
}
