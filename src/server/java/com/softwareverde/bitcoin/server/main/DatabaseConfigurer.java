package com.softwareverde.bitcoin.server.main;

import com.softwareverde.bitcoin.server.configuration.BitcoinProperties;
import com.softwareverde.bitcoin.server.configuration.DatabaseProperties;
import com.softwareverde.database.mysql.embedded.DatabaseCommandLineArguments;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.SystemUtil;

public class DatabaseConfigurer {
    protected static Long toNearestMegabyte(final Long byteCount) {
        return ((byteCount / ByteUtil.Unit.Binary.MEBIBYTES) * ByteUtil.Unit.Binary.MEBIBYTES);
    }

    public static void configureCommandLineArguments(final DatabaseCommandLineArguments commandLineArguments, final Integer maxDatabaseThreadCount, final DatabaseProperties databaseProperties, final BitcoinProperties bitcoinProperties) {
        final long maxHeapTableSize = ((bitcoinProperties != null ? bitcoinProperties.getMaxUtxoCacheByteCount() : 0L) + (16L * ByteUtil.Unit.Binary.MEBIBYTES)); // Include 16MB for MySQL sort tmp-tables...
        commandLineArguments.addArgument("--max_heap_table_size=" + maxHeapTableSize); // Maximum engine=MEMORY table size.

        if (SystemUtil.isWindowsOperatingSystem()) {
            // MariaDb4j currently only supports 32 bit on Windows, so the log file and memory settings must be less than 2 GB...
            commandLineArguments.setInnoDbBufferPoolByteCount(Math.min(ByteUtil.Unit.Binary.GIBIBYTES, databaseProperties.getMaxMemoryByteCount()));
            commandLineArguments.setQueryCacheByteCount(0L);
            commandLineArguments.setMaxAllowedPacketByteCount(128L * ByteUtil.Unit.Binary.MEBIBYTES);
            commandLineArguments.addArgument("--max-connections=" + maxDatabaseThreadCount);
        }
        else {
            final Long maxDatabaseMemory = databaseProperties.getMaxMemoryByteCount();

            final Long logBufferByteCount = DatabaseConfigurer.toNearestMegabyte(Math.min((ByteUtil.Unit.Binary.GIBIBYTES), (maxDatabaseMemory / 4L))); // 1/4th of the max memory, rounded to the nearest Megabyte, but no greater than 1 GB.
            final Long bufferPoolByteCount = DatabaseConfigurer.toNearestMegabyte(maxDatabaseMemory - logBufferByteCount);
            final Integer bufferPoolInstanceCount = Math.max(1, (int) (bufferPoolByteCount / (4L * ByteUtil.Unit.Binary.GIBIBYTES)));

            commandLineArguments.setInnoDbBufferPoolByteCount(bufferPoolByteCount);
            commandLineArguments.setInnoDbBufferPoolInstanceCount(bufferPoolInstanceCount);

            commandLineArguments.setInnoDbLogBufferByteCount(logBufferByteCount);

            commandLineArguments.addArgument("--innodb_io_capacity=2000"); // 2000 for SSDs/NVME, 400 for low-end SSD, 200 for HDD.
            commandLineArguments.addArgument("--innodb-flush-log-at-trx-commit=0"); // Write directly to disk; database crashing may result in data corruption.
            commandLineArguments.addArgument("--innodb-flush-method=O_DIRECT");

            final Long logFileByteCount = DatabaseConfigurer.toNearestMegabyte(databaseProperties.getLogFileByteCount());
            commandLineArguments.setInnoDbLogFileByteCount(logFileByteCount);

            commandLineArguments.setQueryCacheByteCount(0L);

            commandLineArguments.setMaxAllowedPacketByteCount(128L * ByteUtil.Unit.Binary.MEBIBYTES);

            commandLineArguments.addArgument("--max-connections=" + maxDatabaseThreadCount);
            // commandLineArguments.addArgument("--innodb-read-io-threads=8");
            // commandLineArguments.addArgument("--innodb-write-io-threads=8");

            // Experimental setting to improve the flush/write-performance of the InnoDb buffer pool.
            // Suggestion taken from: https://stackoverflow.com/questions/41134785/how-to-solve-mysql-warning-innodb-page-cleaner-1000ms-intended-loop-took-xxx
            commandLineArguments.addArgument("--innodb-lru-scan-depth=256");

            // commandLineArguments.enableSlowQueryLog("slow-query.log", 1L);
            // commandLineArguments.addArgument("--general_log_file=query.log");
            // commandLineArguments.addArgument("--general_log=1");
            commandLineArguments.addArgument("--performance-schema=OFF");
        }
    }

    protected DatabaseConfigurer() { }
}
