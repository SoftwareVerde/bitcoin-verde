package com.softwareverde.network.p2p.node.manager;

import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.concurrent.pool.MainThreadPool;
import com.softwareverde.test.time.FakeSystemTime;
import com.softwareverde.util.Container;
import org.junit.Assert;
import org.junit.Test;

public class PendingRequestsManagerTests {

    @Test
    public void should_trigger_failure_after_timeout() throws Exception {
        // Setup
        final FakeSystemTime systemTime = new FakeSystemTime();
        final MainThreadPool threadPool = new MainThreadPool(1, 1L);
        final PendingRequestsManager<BitcoinNode> pendingRequestsManager = new PendingRequestsManager<BitcoinNode>(systemTime, threadPool);

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
        Thread.sleep(100L); // Pause enough time for the thread to start...
        pendingRequestsManager.stop(); // SleepyService::stop waits for the thread to complete...

        // Assert
        Assert.assertTrue(onFailureContainer.value);
        Assert.assertNull(runContainer.value);
    }
}
