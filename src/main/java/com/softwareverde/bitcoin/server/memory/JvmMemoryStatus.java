package com.softwareverde.bitcoin.server.memory;

import com.softwareverde.io.Logger;

public class JvmMemoryStatus implements MemoryStatus {
    protected final Runtime _runtime;

    protected Long _calculateUsedMemory() { // In Bytes...
        return (_runtime.totalMemory() - _runtime.freeMemory());
    }

    protected Long _getMaxMemory() { // In Bytes...
        return _runtime.maxMemory();
    }

    protected Long _calculateFreeMemory() {
        return (_getMaxMemory() - _calculateUsedMemory());
    }

    public JvmMemoryStatus() {
        _runtime = Runtime.getRuntime();
    }

    @Override
    public Long getByteCountAvailable() {
        return _calculateFreeMemory();
    }

    @Override
    public Long getByteCountUsed() {
        return _calculateUsedMemory();
    }

    /**
     * Returns a value between 0 and 1 representing the percentage of memory used within the JVM.
     */
    @Override
    public Float getMemoryUsedPercent() {
        final Long maxMemory = _getMaxMemory();
        final Long usedMemory = _calculateUsedMemory();

        if (usedMemory == 0L) { return 1.0F; }

        return (usedMemory.floatValue() / maxMemory.floatValue());
    }

    @Override
    public void logCurrentMemoryUsage() {
        Logger.log("Current JVM Memory Usage : " + (_calculateFreeMemory()) + " bytes | MAX=" + _getMaxMemory() + " TOTAL=" + _runtime.totalMemory() + " FREE=" + _runtime.freeMemory());
    }
}
