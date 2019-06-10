package com.softwareverde.bitcoin.server.main;

import com.softwareverde.bitcoin.server.configuration.DatabaseProperties;
import com.softwareverde.database.mysql.embedded.DatabaseCommandLineArguments;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.SystemUtil;

public class DatabaseConfigurer {
    protected static Long toNearestMegabyte(final Long byteCount) {
        return ((byteCount / ByteUtil.Unit.MEGABYTES) * ByteUtil.Unit.MEGABYTES);
    }

    public static void configureCommandLineArguments(final DatabaseCommandLineArguments commandLineArguments, final Integer maxDatabaseThreadCount, final DatabaseProperties databaseProperties) {
        if (SystemUtil.isWindowsOperatingSystem()) {
            // MariaDb4j currently only supports 32 bit on Windows, so the log file and memory settings must be less than 2 GB...
            commandLineArguments.setInnoDbBufferPoolByteCount(Math.min(ByteUtil.Unit.GIGABYTES, databaseProperties.getMaxMemoryByteCount()));
            commandLineArguments.setQueryCacheByteCount(0L);
            commandLineArguments.setMaxAllowedPacketByteCount(128 * ByteUtil.Unit.MEGABYTES);
            commandLineArguments.addArgument("--max-connections=" + maxDatabaseThreadCount);
        }
        else {
            final Long maxDatabaseMemory = databaseProperties.getMaxMemoryByteCount();

            final Long logBufferByteCount = DatabaseConfigurer.toNearestMegabyte(Math.min((1L * ByteUtil.Unit.GIGABYTES), (maxDatabaseMemory / 4L))); // 1/4th of the max memory, rounded to the nearest Megabyte, but no greater than 1 GB.
            final Long bufferPoolByteCount = DatabaseConfigurer.toNearestMegabyte(maxDatabaseMemory - logBufferByteCount);
            final Integer bufferPoolInstanceCount = Math.max(1, (int) (bufferPoolByteCount / (4L * ByteUtil.Unit.GIGABYTES)));

            commandLineArguments.setInnoDbBufferPoolByteCount(bufferPoolByteCount);
            commandLineArguments.setInnoDbBufferPoolInstanceCount(bufferPoolInstanceCount);

            commandLineArguments.setInnoDbLogBufferByteCount(logBufferByteCount);

            commandLineArguments.addArgument("--innodb-flush-log-at-trx-commit=0");
            commandLineArguments.addArgument("--innodb-flush-method=O_DIRECT");

            commandLineArguments.setInnoDbLogFileByteCount(32 * ByteUtil.Unit.GIGABYTES);

            commandLineArguments.setQueryCacheByteCount(0L);

            commandLineArguments.setMaxAllowedPacketByteCount(128 * ByteUtil.Unit.MEGABYTES);

            commandLineArguments.addArgument("--max-connections=" + maxDatabaseThreadCount);
            commandLineArguments.addArgument("--innodb-read-io-threads=8");
            commandLineArguments.addArgument("--innodb-write-io-threads=8");

            // Experimental setting to improve the flush/write-performance of the InnoDb buffer pool.
            // Suggestion taken from: https://stackoverflow.com/questions/41134785/how-to-solve-mysql-warning-innodb-page-cleaner-1000ms-intended-loop-took-xxx
            commandLineArguments.addArgument("--innodb-lru-scan-depth=256");

            // commandLineArguments.enableSlowQueryLog("slow-query.log", 1L);
            // commandLineArguments.addArgument("--performance_schema");
            // commandLineArguments.addArgument("--general_log_file=query.log");
            // commandLineArguments.addArgument("--general_log=1");
        }
    }

    protected DatabaseConfigurer() { }
}
