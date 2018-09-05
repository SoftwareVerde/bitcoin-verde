package com.softwareverde.network.p2p.node.manager.health;

import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.util.type.time.Time;
import org.junit.Assert;
import org.junit.Test;

public class NodeHealthTests {
    private class FakeTime implements Time {
        protected Long _timeMs = 0L; // System.currentTimeMillis();

        @Override
        public Long getCurrentTimeInSeconds() {
            return (_timeMs / 1_000L);
        }

        @Override
        public Long getCurrentTimeInMilliSeconds() {
            return _timeMs;
        }

        public void advanceTimeInMilliseconds(final Long milliseconds) {
            _timeMs += milliseconds;
        }
    }

    @Test
    public void should_have_full_health_initially() {
        // Setup
        final FakeTime fakeTime = new FakeTime();
        final NodeHealth nodeHealth = new NodeHealth(NodeId.wrap(1L), fakeTime);

        // Action
        final Long nodeHealthValue = nodeHealth.calculateHealth();

        // Assert
        Assert.assertEquals(NodeHealth.FULL_HEALTH, nodeHealthValue);
    }

    @Test
    public void should_reduce_health_for_request() {
        // Setup
        final FakeTime fakeTime = new FakeTime();
        final NodeHealth nodeHealth = new NodeHealth(NodeId.wrap(1L), fakeTime);

        // Action
        final NodeHealth.Request request = nodeHealth.onMessageSent();
        fakeTime.advanceTimeInMilliseconds(250L);
        nodeHealth.onMessageReceived(request);

        final Long nodeHealthValue = nodeHealth.calculateHealth();

        // Assert
        Assert.assertEquals(NodeHealth.FULL_HEALTH - 250L, nodeHealthValue.longValue());
    }

    @Test
    public void should_heal_after_duration_of_recent_requests() {
        // Setup
        final FakeTime fakeTime = new FakeTime();
        final NodeHealth nodeHealth = new NodeHealth(NodeId.wrap(1L), fakeTime);

        // Action
        final NodeHealth.Request request = nodeHealth.onMessageSent();
        fakeTime.advanceTimeInMilliseconds(250L);
        nodeHealth.onMessageReceived(request);
        fakeTime.advanceTimeInMilliseconds(250L);

        final Long nodeHealthValue = nodeHealth.calculateHealth();

        // Assert
        Assert.assertEquals(NodeHealth.FULL_HEALTH, nodeHealthValue);
    }

    @Test
    public void should_heal_after_duration_of_recent_requests_2() {
        // Setup
        final FakeTime fakeTime = new FakeTime();
        final NodeHealth nodeHealth = new NodeHealth(NodeId.wrap(1L), fakeTime);

        // Action
        final NodeHealth.Request request = nodeHealth.onMessageSent();
        fakeTime.advanceTimeInMilliseconds(250L);
        nodeHealth.onMessageReceived(request);
        fakeTime.advanceTimeInMilliseconds(200L);

        final Long nodeHealthValue = nodeHealth.calculateHealth();

        // Assert
        Assert.assertEquals(NodeHealth.FULL_HEALTH - 50L, nodeHealthValue.longValue());
    }

    @Test
    public void should_heal_after_duration_of_recent_requests_3() {
        // Setup
        final FakeTime fakeTime = new FakeTime();
        final NodeHealth nodeHealth = new NodeHealth(NodeId.wrap(1L), fakeTime);

        // Action
        NodeHealth.Request request = nodeHealth.onMessageSent();
        fakeTime.advanceTimeInMilliseconds(250L);
        nodeHealth.onMessageReceived(request);
        fakeTime.advanceTimeInMilliseconds(250L);

        request = nodeHealth.onMessageSent();
        fakeTime.advanceTimeInMilliseconds(250L);
        nodeHealth.onMessageReceived(request);

        final Long nodeHealthValue = nodeHealth.calculateHealth();

        // Assert
        Assert.assertEquals(NodeHealth.FULL_HEALTH - 250L, nodeHealthValue.longValue());
    }

    @Test
    public void should_not_heal_past_max_health() {
        // Setup
        final FakeTime fakeTime = new FakeTime();
        final NodeHealth nodeHealth = new NodeHealth(NodeId.wrap(1L), fakeTime);

        // Action
        final NodeHealth.Request request = nodeHealth.onMessageSent();
        fakeTime.advanceTimeInMilliseconds(200L);
        nodeHealth.onMessageReceived(request);
        fakeTime.advanceTimeInMilliseconds(250L);

        final Long nodeHealthValue = nodeHealth.calculateHealth();

        // Assert
        Assert.assertEquals(NodeHealth.FULL_HEALTH, nodeHealthValue);
    }

    @Test
    public void should_not_account_for_requests_that_are_too_old() {
        // Setup
        final FakeTime fakeTime = new FakeTime();
        final NodeHealth nodeHealth = new NodeHealth(NodeId.wrap(1L), fakeTime);

        // Action
        { // Drop the nodeHealth to zero...
            for (int i = 0; i < 1024; ++i) {
                final NodeHealth.Request request = nodeHealth.onMessageSent();
                fakeTime.advanceTimeInMilliseconds(100L);
                nodeHealth.onMessageReceived(request);
            }
        }
        Assert.assertEquals(0L, nodeHealth.calculateHealth().longValue());

        fakeTime.advanceTimeInMilliseconds(1L); // The last request drops off, "restoring" 100 hp and actually restoring 1 hp...
        Assert.assertEquals(101L, nodeHealth.calculateHealth().longValue());

        fakeTime.advanceTimeInMilliseconds(50L); // Restores an additional 50 hp...

        final Long nodeHealthValue = nodeHealth.calculateHealth();

        // Assert
        Assert.assertEquals(151L, nodeHealthValue.longValue());
    }
}
