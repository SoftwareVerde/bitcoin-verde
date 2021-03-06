package com.softwareverde.bitcoin.server.main;

import com.softwareverde.bitcoin.server.configuration.BitcoinProperties;
import com.softwareverde.bitcoin.server.configuration.DatabaseProperties;
import com.softwareverde.database.mysql.embedded.DatabaseCommandLineArguments;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.SystemUtil;

public class DatabaseConfigurer {
    public static Long toNearestMegabyte(final Long byteCount) {
        return ((byteCount / ByteUtil.Unit.Binary.MEBIBYTES) * ByteUtil.Unit.Binary.MEBIBYTES);
    }

    public static void configureCommandLineArguments(final DatabaseCommandLineArguments commandLineArguments, final Integer maxDatabaseThreadCount, final DatabaseProperties databaseProperties, final BitcoinProperties bitcoinProperties) {
        // TODO: too many arguments:
        // commandLineArguments.addArgument("--sql-mode='STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_AUTO_CREATE_USER,NO_ENGINE_SUBSTITUTION'"); // Disable ONLY_FULL_GROUP_BY.

        final long connectionsReservedForRoot = 1;
        final long maxAllowedPacketByteCount = (32L * ByteUtil.Unit.Binary.MEBIBYTES);
        commandLineArguments.setMaxAllowedPacketByteCount(maxAllowedPacketByteCount);

        if (SystemUtil.isWindowsOperatingSystem()) {
            // MariaDb4j currently only supports 32 bit on Windows, so the log file and memory settings must be less than 2 GB...
            commandLineArguments.setInnoDbBufferPoolByteCount(Math.min(ByteUtil.Unit.Binary.GIBIBYTES, databaseProperties.getMaxMemoryByteCount()));
            commandLineArguments.setQueryCacheByteCount(0L);
            commandLineArguments.addArgument("--max-connections=" + (maxDatabaseThreadCount + connectionsReservedForRoot));
        }
        else {
            // The default per_thread_buffer is roughly 3mb excluding the max_allowed_packet.
            //  per_thread_buffer = read_buffer_size + read_rnd_buffer_size + sort_buffer_size + thread_stack + join_buffer_size + max_allowed_packet

            // Technically for the worst-case scenario, the bytesPerConnection should be a function of maxAllowedPacketByteCount,
            //  but since the vast majority of queries only use a fraction of this amount, the calculation takes the "steadyState" packet size instead.
            final long steadyStatePacketSize = (8L * ByteUtil.Unit.Binary.MEBIBYTES);
            final long bytesPerConnection = ((3L * ByteUtil.Unit.Binary.MEBIBYTES) + steadyStatePacketSize);
            final long overheadByteCount = (bytesPerConnection * maxDatabaseThreadCount);

            final long minMemoryRequired = (256L * ByteUtil.Unit.Binary.MEBIBYTES);
            final long maxDatabaseMemory = (databaseProperties.getMaxMemoryByteCount() - overheadByteCount);
            if (maxDatabaseMemory < minMemoryRequired) {
                throw new RuntimeException("Insufficient memory available to allocate desired settings. Consider reducing the number of allowed connections.");
            }

            final Long logFileByteCount = DatabaseConfigurer.toNearestMegabyte(databaseProperties.getLogFileByteCount());
            final Long logBufferByteCount = DatabaseConfigurer.toNearestMegabyte(Math.min((logFileByteCount / 4L), (maxDatabaseMemory / 4L))); // 25% of the logFile size but no larger than 25% of the total database memory.
            final Long bufferPoolByteCount = DatabaseConfigurer.toNearestMegabyte(maxDatabaseMemory - logBufferByteCount);
            final int bufferPoolInstanceCount;
            { // https://www.percona.com/blog/2020/08/13/how-many-innodb_buffer_pool_instances-do-you-need-in-mysql-8/
                final int segmentCount = (int) (bufferPoolByteCount / (256L * ByteUtil.Unit.Binary.MEBIBYTES));
                if (segmentCount < 1) { bufferPoolInstanceCount = 1; }
                else if (segmentCount > 32) { bufferPoolInstanceCount = 32; }
                else { bufferPoolInstanceCount = segmentCount; }
            }

            // TODO: Experiment with innodb_change_buffer_max_size and innodb_change_buffering for better IBD performance.

            commandLineArguments.setInnoDbLogFileByteCount(logFileByteCount); // Redo Log file (on-disk)
            commandLineArguments.setInnoDbLogBufferByteCount(logBufferByteCount); // Redo Log (in-memory)
            commandLineArguments.setInnoDbBufferPoolByteCount(bufferPoolByteCount); // Innodb Dirty Page Cache
            commandLineArguments.setInnoDbBufferPoolInstanceCount(bufferPoolInstanceCount); // Innodb Dirt Page Concurrency

            commandLineArguments.addArgument("--innodb-io-capacity=2000"); // 2000 for SSDs/NVME, 400 for low-end SSD, 200 for HDD.
            commandLineArguments.addArgument("--innodb-flush-log-at-trx-commit=0"); // Write directly to disk; database crashing may result in data corruption.
            commandLineArguments.addArgument("--innodb-flush-method=O_DIRECT");

            commandLineArguments.addArgument("--innodb-io-capacity=5000"); // 2000 for SSDs/NVME, 400 for low-end SSD, 200 for HDD.  Higher encourages fewer dirty pages passively.
            commandLineArguments.addArgument("--innodb-io-capacity-max=10000"); // Higher facilitates active dirty-page flushing.
            commandLineArguments.addArgument("--innodb-page-cleaners=" + bufferPoolInstanceCount);
            commandLineArguments.addArgument("--innodb-max-dirty-pages-pct=0"); // Encourage no dirty buffers in order to facilitate faster writes.
            commandLineArguments.addArgument("--innodb-max-dirty-pages-pct-lwm=5"); // Encourage no dirty buffers in order to facilitate faster writes.
            commandLineArguments.addArgument("--innodb-read-io-threads=16");
            commandLineArguments.addArgument("--innodb-write-io-threads=32");
            commandLineArguments.addArgument("--innodb-lru-scan-depth=2048");
            commandLineArguments.addArgument("--myisam-sort-buffer-size=4096"); // Reduce per-connection memory allocation (only used for MyISAM DDL statements).

            commandLineArguments.setQueryCacheByteCount(null); // Deprecated, removed in Mysql 8.
            commandLineArguments.addArgument("--max-connections=" + (maxDatabaseThreadCount + connectionsReservedForRoot));

            // commandLineArguments.enableSlowQueryLog("slow-query.log", 1L);
            // commandLineArguments.addArgument("--general-log-file=query.log");
            // commandLineArguments.addArgument("--general-log=1");
            commandLineArguments.addArgument("--performance-schema=OFF");
        }
    }

    protected DatabaseConfigurer() { }
}
