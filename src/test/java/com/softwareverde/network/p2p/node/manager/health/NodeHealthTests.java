package com.softwareverde.network.p2p.node.manager.health;

import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.bitcoin.type.time.Time;
import org.junit.Assert;
import org.junit.Test;

public class NodeHealthTests {
    private class FakeTime implements Time {
        protected Long _timeMs = System.currentTimeMillis();

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

    protected void _configureNodeHealth(final NodeHealth nodeHealth) {
        nodeHealth.setHealthPerSecond(10);
        nodeHealth.setHealthConsumedPerRequest(10);
        nodeHealth.setMaxHealth(100);
    }

    @Test
    public void should_have_perfect_health_initially() {
        // Setup
        final FakeTime fakeTime = new FakeTime();
        final NodeHealth nodeHealth = new NodeHealth(NodeId.wrap(1L), fakeTime);
        _configureNodeHealth(nodeHealth);

        // Action
        final Integer nodeHealthValue = nodeHealth.calculateHealth();

        // Assert
        Assert.assertEquals(100, nodeHealthValue.intValue());
    }

    @Test
    public void should_decay_10_percent_after_recent_message() {
        // Setup
        final FakeTime fakeTime = new FakeTime();
        final NodeHealth nodeHealth = new NodeHealth(NodeId.wrap(1L), fakeTime);
        _configureNodeHealth(nodeHealth);

        nodeHealth.onMessageSent();

        // Action
        final Integer nodeHealthValue = nodeHealth.calculateHealth();

        // Assert
        Assert.assertEquals(90, nodeHealthValue.intValue());
    }

    @Test
    public void should_decay_100_percent_on_first_message_failure() {
        // Setup
        final FakeTime fakeTime = new FakeTime();
        final NodeHealth nodeHealth = new NodeHealth(NodeId.wrap(1L), fakeTime);
        _configureNodeHealth(nodeHealth);

        nodeHealth.onMessageReceived(false);

        // Action
        final Integer nodeHealthValue = nodeHealth.calculateHealth();

        // Assert
        Assert.assertEquals(0, nodeHealthValue.intValue());
    }

    @Test
    public void should_decay_50_percent_on_first_message_success_then_second_failure() {
        // Setup
        final FakeTime fakeTime = new FakeTime();
        final NodeHealth nodeHealth = new NodeHealth(NodeId.wrap(1L), fakeTime);
        _configureNodeHealth(nodeHealth);

        nodeHealth.onMessageReceived(true);
        fakeTime.advanceTimeInMilliseconds(10_000L);
        nodeHealth.onMessageReceived(false);
        fakeTime.advanceTimeInMilliseconds(10_000L);

        // Action
        final Integer nodeHealthValue = nodeHealth.calculateHealth();

        // Assert
        Assert.assertEquals(50, nodeHealthValue.intValue());
    }

    @Test
    public void should_regenerate_health_after_time() {
        // Setup
        final FakeTime fakeTime = new FakeTime();
        final NodeHealth nodeHealth = new NodeHealth(NodeId.wrap(1L), fakeTime);
        _configureNodeHealth(nodeHealth);

        nodeHealth.onMessageSent(); // 90
        fakeTime.advanceTimeInMilliseconds(500L); // 95
        nodeHealth.onMessageSent(); // 85

        // Action
        final Integer nodeHealthValue = nodeHealth.calculateHealth();

        // Assert
        Assert.assertEquals(85, nodeHealthValue.intValue());
    }

    @Test
    public void should_regenerate_health_after_time_2() {
        // Setup
        final FakeTime fakeTime = new FakeTime();
        final NodeHealth nodeHealth = new NodeHealth(NodeId.wrap(1L), fakeTime);
        _configureNodeHealth(nodeHealth);

        nodeHealth.onMessageSent(); // 90
        nodeHealth.onMessageSent(); // 80
        nodeHealth.onMessageSent(); // 70
        nodeHealth.onMessageSent(); // 60
        fakeTime.advanceTimeInMilliseconds(1_000L); // 70
        nodeHealth.onMessageSent(); // 60

        // Action
        final Integer nodeHealthValue = nodeHealth.calculateHealth();

        // Assert
        Assert.assertEquals(60, nodeHealthValue.intValue());
    }

    @Test
    public void should_regenerate_health_after_time_3() {
        // Setup
        final FakeTime fakeTime = new FakeTime();
        final NodeHealth nodeHealth = new NodeHealth(NodeId.wrap(1L), fakeTime);
        _configureNodeHealth(nodeHealth);

        nodeHealth.onMessageSent(); // 90
        fakeTime.advanceTimeInMilliseconds(1_000L); // 100
        nodeHealth.onMessageSent(); // 90
        fakeTime.advanceTimeInMilliseconds(100L); // 91
        nodeHealth.onMessageSent(); // 81
        fakeTime.advanceTimeInMilliseconds(100L); // 82
        nodeHealth.onMessageSent(); // 72
        fakeTime.advanceTimeInMilliseconds(90L); // 72 (Nothing (Rounded Down))
        nodeHealth.onMessageSent(); // 62
        fakeTime.advanceTimeInMilliseconds(10L); // 73 (90ms remaining from previous calculation...)

        // Action
        final Integer nodeHealthValue = nodeHealth.calculateHealth();

        // Assert
        Assert.assertEquals(63, nodeHealthValue.intValue());
    }

    @Test
    public void should_disregard_old_failures() {
        // Setup
        final FakeTime fakeTime = new FakeTime();
        final NodeHealth nodeHealth = new NodeHealth(NodeId.wrap(1L), fakeTime);
        _configureNodeHealth(nodeHealth);

        nodeHealth.onMessageSent(); // 90
        fakeTime.advanceTimeInMilliseconds(110L); // 91
        nodeHealth.onMessageReceived(false); // 0
        fakeTime.advanceTimeInMilliseconds(60_050L); // 100
        nodeHealth.onMessageSent(); // 90
        fakeTime.advanceTimeInMilliseconds(110L); // 91
        nodeHealth.onMessageReceived(true); // 91

        // Action
        final Integer nodeHealthValue = nodeHealth.calculateHealth();

        // Assert
        Assert.assertEquals(91, nodeHealthValue.intValue());
    }

    @Test
    public void should_disregard_old_failures_2() {
        // Setup
        final FakeTime fakeTime = new FakeTime();
        final NodeHealth nodeHealth = new NodeHealth(NodeId.wrap(1L), fakeTime);
        _configureNodeHealth(nodeHealth);

        nodeHealth.onMessageSent(); // 90
        fakeTime.advanceTimeInMilliseconds(110L); // 91
        nodeHealth.onMessageReceived(false); // 91 * 0.0F
        fakeTime.advanceTimeInMilliseconds(60_050L); // 100
        nodeHealth.onMessageSent(); // 90
        fakeTime.advanceTimeInMilliseconds(110L); // 91
        nodeHealth.onMessageReceived(false); // 91 * 0.0F
        nodeHealth.onMessageSent(); // 81 * 0.0F
        fakeTime.advanceTimeInMilliseconds(110L); // 82 * 0.0F
        nodeHealth.onMessageReceived(true); // 82 * 0.5F

        // Action
        final Integer nodeHealthValue = nodeHealth.calculateHealth();

        // Assert
        Assert.assertEquals(41, nodeHealthValue.intValue());
    }
}
