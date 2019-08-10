package com.softwareverde.bitcoin.server.main;

import com.softwareverde.bitcoin.server.memory.MemoryStatus;
import com.softwareverde.logging.Logger;
import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;

public class SystemMemoryStatus implements MemoryStatus {
    protected final OperatingSystemMXBean _operatingSystem;

    protected Long _calculateUsedMemory() { // In Bytes...
        if (_operatingSystem == null) { return 0L; }

        return (_operatingSystem.getTotalPhysicalMemorySize() - _operatingSystem.getFreePhysicalMemorySize());
    }

    protected Long _calculateMaxMemory() { // In Bytes...
        if (_operatingSystem == null) { return 0L; }

        return _operatingSystem.getTotalPhysicalMemorySize();
    }

    public SystemMemoryStatus() {
        final java.lang.management.OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
        _operatingSystem = ( (operatingSystemMXBean instanceof OperatingSystemMXBean) ? (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean() : null );
    }

    @Override
    public Long getByteCountAvailable() {
        return (_calculateMaxMemory() - _calculateUsedMemory());
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
        final Long maxMemory = _calculateMaxMemory();
        final Long usedMemory = _calculateUsedMemory();

        if (usedMemory == 0L) { return 1.0F; }

        return (usedMemory.floatValue() / maxMemory.floatValue());
    }

    @Override
    public void logCurrentMemoryUsage() {
        Logger.info("Current System Memory Usage : " + _operatingSystem.getFreePhysicalMemorySize() + " bytes | MAX=" + _calculateMaxMemory());
    }
}
