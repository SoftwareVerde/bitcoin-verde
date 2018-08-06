package com.softwareverde.network.p2p.node.manager;

public class ReplayInvocation implements Runnable {
    private Integer _replayCount = 0;
    private final Runnable _replayRunnable;
    private final Runnable _failureRunnable;

    public ReplayInvocation(final Runnable runnable, final Runnable failureRunnable) {
        _replayRunnable = runnable;
        _failureRunnable = failureRunnable;
    }

    @Override
    public final void run() {
        _replayCount += 1;
        _replayRunnable.run();
    }

    public void fail() {
        if (_failureRunnable != null) {
            _failureRunnable.run();
        }
    }

    public Integer getReplayCount() {
        return _replayCount;
    }
}
