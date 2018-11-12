package com.softwareverde.bitcoin.server.memory;

public interface MemoryStatus {
    Long getByteCountAvailable();
    Long getByteCountUsed();

    /**
     * Returns a value between 0 and 1 representing the percentage of memory used within the JVM.
     */
    Float getMemoryUsedPercent();

    void logCurrentMemoryUsage();
}
