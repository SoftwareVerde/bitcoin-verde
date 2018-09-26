package com.softwareverde.network.p2p.node.manager.health;

import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.test.time.FakeSystemTime;
import org.junit.Assert;
import org.junit.Test;

public class NodeHealthTests {
    @Test
    public void should_have_full_health_initially() {
        // Setup
        final FakeSystemTime fakeTime = new FakeSystemTime();
        final NodeHealth nodeHealth = new NodeHealth(NodeId.wrap(1L), fakeTime);

        // Action
        final Long nodeHealthValue = nodeHealth.calculateHealth();

        // Assert
        Assert.assertEquals(NodeHealth.FULL_HEALTH, nodeHealthValue);
    }

    @Test
    public void should_reduce_health_for_request() {
        // Setup
        final FakeSystemTime fakeTime = new FakeSystemTime();
        final NodeHealth nodeHealth = new NodeHealth(NodeId.wrap(1L), fakeTime);

        final Long execTime = 250L;
        final Long restTime = 0L;
        final Long damage = (long) (execTime / NodeHealth.REGEN_TO_REQUEST_TIME_RATIO);
        final Long expectedHealth = (NodeHealth.FULL_HEALTH - damage + restTime);

        // Action
        final NodeHealth.Request request = nodeHealth.onRequestSent();
        fakeTime.advanceTimeInMilliseconds(execTime);
        nodeHealth.onResponseReceived(request);
        fakeTime.advanceTimeInMilliseconds(restTime);

        final Long nodeHealthValue = nodeHealth.calculateHealth();

        // Assert
        Assert.assertEquals(expectedHealth, nodeHealthValue);
    }

    @Test
    public void should_heal_after_duration_of_recent_requests() {
        // Setup
        final FakeSystemTime fakeTime = new FakeSystemTime();
        final NodeHealth nodeHealth = new NodeHealth(NodeId.wrap(1L), fakeTime);

        final Long execTime = 250L;
        final Long restTime = (long) (250L / NodeHealth.REGEN_TO_REQUEST_TIME_RATIO);
        final Long expectedHealth = NodeHealth.FULL_HEALTH;

        // Action
        final NodeHealth.Request request = nodeHealth.onRequestSent();
        fakeTime.advanceTimeInMilliseconds(execTime);
        nodeHealth.onResponseReceived(request);
        fakeTime.advanceTimeInMilliseconds(restTime);

        final Long nodeHealthValue = nodeHealth.calculateHealth();

        // Assert
        Assert.assertEquals(expectedHealth, nodeHealthValue);
    }

    @Test
    public void should_heal_after_duration_of_recent_requests_2() {
        // Setup
        final FakeSystemTime fakeTime = new FakeSystemTime();
        final NodeHealth nodeHealth = new NodeHealth(NodeId.wrap(1L), fakeTime);

        final Long execTime = 250L;
        final Long restTime = 200L;
        final Long damage = (long) (execTime / NodeHealth.REGEN_TO_REQUEST_TIME_RATIO);
        final Long expectedHealth = (NodeHealth.FULL_HEALTH - damage + restTime);

        // Action
        final NodeHealth.Request request = nodeHealth.onRequestSent();
        fakeTime.advanceTimeInMilliseconds(execTime);
        nodeHealth.onResponseReceived(request);
        fakeTime.advanceTimeInMilliseconds(restTime);

        final Long nodeHealthValue = nodeHealth.calculateHealth();

        // Assert
        Assert.assertEquals(expectedHealth, nodeHealthValue);
    }

    @Test
    public void should_heal_after_duration_of_recent_requests_3() {
        // Setup
        final FakeSystemTime fakeTime = new FakeSystemTime();
        final NodeHealth nodeHealth = new NodeHealth(NodeId.wrap(1L), fakeTime);

        final Long execCount = 2L;
        final Long execTime = 250L;
        final Long restTime = 200L;
        final Long damage = (long) (execTime / NodeHealth.REGEN_TO_REQUEST_TIME_RATIO);
        final Long expectedHealth = (NodeHealth.FULL_HEALTH - (damage * execCount) + restTime);

        NodeHealth.Request request;

        // Action
        request = nodeHealth.onRequestSent(); // execCount = 1
        fakeTime.advanceTimeInMilliseconds(execTime);
        nodeHealth.onResponseReceived(request);
        fakeTime.advanceTimeInMilliseconds(restTime);

        request = nodeHealth.onRequestSent(); // execCount = 2
        fakeTime.advanceTimeInMilliseconds(execTime);
        nodeHealth.onResponseReceived(request);

        final Long nodeHealthValue = nodeHealth.calculateHealth();

        // Assert
        Assert.assertEquals(expectedHealth, nodeHealthValue);
    }

    @Test
    public void should_not_heal_past_max_health() {
        // Setup
        final FakeSystemTime fakeTime = new FakeSystemTime();
        final NodeHealth nodeHealth = new NodeHealth(NodeId.wrap(1L), fakeTime);

        // Action
        final NodeHealth.Request request = nodeHealth.onRequestSent();
        fakeTime.advanceTimeInMilliseconds(200L);
        nodeHealth.onResponseReceived(request);
        fakeTime.advanceTimeInMilliseconds(9999L);

        final Long nodeHealthValue = nodeHealth.calculateHealth();

        // Assert
        Assert.assertEquals(NodeHealth.FULL_HEALTH, nodeHealthValue);
    }

    @Test
    public void should_not_account_for_requests_that_are_too_old() {
        // Setup
        final FakeSystemTime fakeTime = new FakeSystemTime();
        fakeTime.advanceTimeInMilliseconds(NodeHealth.FULL_HEALTH); // Required to prevent minimumRequestTime from going negative...

        final NodeHealth nodeHealth = new NodeHealth(NodeId.wrap(1L), fakeTime);

        final Long requestTime = (NodeHealth.FULL_HEALTH / 1024);

        // Action
        { // Drop the nodeHealth to zero...
            for (int i = 0; i < 2028; ++i) { // Only half of these are retained...
                final NodeHealth.Request request = nodeHealth.onRequestSent();
                fakeTime.advanceTimeInMilliseconds(requestTime);
                nodeHealth.onResponseReceived(request);
            }
        }
        Assert.assertEquals(0L, nodeHealth.calculateHealth().longValue());

        fakeTime.advanceTimeInMilliseconds(requestTime);
        Assert.assertEquals(requestTime, nodeHealth.calculateHealth());

        fakeTime.advanceTimeInMilliseconds(50L); // Restores an additional 50 hp...

        final Long nodeHealthValue = nodeHealth.calculateHealth();

        // Assert
        Assert.assertEquals(requestTime + 50L, nodeHealthValue.longValue());

        // Also assert the node eventually heals to full...
        fakeTime.advanceTimeInMilliseconds(NodeHealth.FULL_HEALTH);
        Assert.assertEquals(NodeHealth.FULL_HEALTH, nodeHealth.calculateHealth());
    }
}
