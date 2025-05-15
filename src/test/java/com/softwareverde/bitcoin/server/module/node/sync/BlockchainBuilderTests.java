package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;

public class BlockchainBuilderTests {
    /**
     * A BlockDownloader StatusMonitor that is always in the `ACTIVE` state.
     */
    public static final BlockDownloader.StatusMonitor FAKE_DOWNLOAD_STATUS_MONITOR = new SleepyService.StatusMonitor() {
        @Override
        public SleepyService.Status getStatus() {
            return SleepyService.Status.ACTIVE;
        }
    };

    /**
     * FakeBitcoinNodeManager is a BitcoinNodeManager that prevents all network traffic.
     */
    public static class FakeBitcoinNodeManager extends BitcoinNodeManager {
        protected static Context _createFakeContext() {
            final Context context = new Context();
            context.maxNodeCount = 0;
            return context;
        }

        public FakeBitcoinNodeManager() {
            super(_createFakeContext());
        }

        @Override
        public List<BitcoinNode> getNodes() {
            return new MutableArrayList<>(0);
        }
    }
}
