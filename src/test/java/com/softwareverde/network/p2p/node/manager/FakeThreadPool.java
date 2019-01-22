package com.softwareverde.network.p2p.node.manager;

import com.softwareverde.concurrent.pool.MainThreadPool;

public class FakeThreadPool extends MainThreadPool {
    public FakeThreadPool() {
        super(0, 0L);
    }

    @Override
    public void execute(final Runnable runnable) {
        if (runnable.toString().contains("TimeoutRunnable")) {
            return; // Skip running any timeout runnables...
        }

        runnable.run();
    }
}