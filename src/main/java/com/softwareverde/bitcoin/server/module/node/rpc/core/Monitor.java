package com.softwareverde.bitcoin.server.module.node.rpc.core;

public interface Monitor {
    Boolean isComplete();
    Long getDurationMs();

    void setMaxDurationMs(Long maxDurationMs);
    void cancel();
}
