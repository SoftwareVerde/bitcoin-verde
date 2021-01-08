package com.softwareverde.bitcoin.rpc.monitor;

public interface Monitor {
    Boolean isComplete();
    Long getDurationMs();

    void setMaxDurationMs(Long maxDurationMs);
    void cancel();
}
