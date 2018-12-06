package com.softwareverde.network.p2p.node.manager;

import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.test.time.FakeSystemTime;
import com.softwareverde.util.Container;
import org.junit.Assert;
import org.junit.Test;

public class PendingRequestsManagerTests {

    @Test
    public void should_trigger_failure_after_timeout() {
        // Setup
        final FakeSystemTime systemTime = new FakeSystemTime();
        final PendingRequestsManager<BitcoinNode> pendingRequestsManager = new PendingRequestsManager<BitcoinNode>(systemTime);

        final Container<Boolean> onFailureContainer = new Container<Boolean>(false);
        final Container<BitcoinNode> runContainer = new Container<BitcoinNode>(null);
        final NodeManager.NodeApiRequest<BitcoinNode> nodeNodeApiRequest = new NodeManager.NodeApiRequest<BitcoinNode>() {
            @Override
            public void onFailure() {
                onFailureContainer.value = true;
            }

            @Override
            public void run(final BitcoinNode bitcoinNode) {
                runContainer.value = bitcoinNode;
            }
        };

        pendingRequestsManager.addPendingRequest(nodeNodeApiRequest);

        // Action
        systemTime.advanceTimeInMilliseconds(PendingRequestsManager.TIMEOUT_MS + 1L);
        pendingRequestsManager.start();
        pendingRequestsManager.stop(); // SleepyService::stop waits for the thread to complete...

        // Assert
        Assert.assertTrue(onFailureContainer.value);
        Assert.assertNull(runContainer.value);
    }
}
