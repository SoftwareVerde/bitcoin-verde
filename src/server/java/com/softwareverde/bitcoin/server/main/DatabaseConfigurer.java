package com.softwareverde.bitcoin.server.main;

import com.softwareverde.bitcoin.server.configuration.BitcoinVerdeDatabaseProperties;
import com.softwareverde.database.mysql.embedded.properties.EmbeddedDatabaseProperties;
import com.softwareverde.database.mysql.embedded.properties.MutableEmbeddedDatabaseProperties;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.SystemUtil;

import java.io.File;

public class DatabaseConfigurer {

    public static Long getBytesPerDatabaseConnection() {
        // The default per_thread_buffer is roughly 3mb excluding the max_allowed_packet.
        //  per_thread_buffer = read_buffer_size + read_rnd_buffer_size + sort_buffer_size + thread_stack + join_buffer_size + max_allowed_packet

        // Technically for the worst-case scenario, the bytesPerConnection should be a function of maxAllowedPacketByteCount,
        //  but since the vast majority of queries only use a fraction of this amount, the calculation takes the "steadyState" packet size instead.
        final long steadyStatePacketSize = (8L * ByteUtil.Unit.Binary.MEBIBYTES);
        final long bytesPerConnection = ((3L * ByteUtil.Unit.Binary.MEBIBYTES) + steadyStatePacketSize);
        return bytesPerConnection;
    }

    public static Long toNearestMegabyte(final Long byteCount) {
        return ((byteCount / ByteUtil.Unit.Binary.MEBIBYTES) * ByteUtil.Unit.Binary.MEBIBYTES);
    }

    public static EmbeddedDatabaseProperties configureDatabase(final Integer maxDatabaseConnectionCount, final BitcoinVerdeDatabaseProperties bitcoinVerdeDatabaseProperties) {
        final MutableEmbeddedDatabaseProperties embeddedDatabaseProperties = new MutableEmbeddedDatabaseProperties(bitcoinVerdeDatabaseProperties);

        { // Copy configuration settings specific to BitcoinVerdeDatabaseProperties from the provided property set...
            final File installationDirectory = bitcoinVerdeDatabaseProperties.getInstallationDirectory();
            final File dataDirectory = bitcoinVerdeDatabaseProperties.getDataDirectory();

            embeddedDatabaseProperties.setInstallationDirectory(installationDirectory);
            embeddedDatabaseProperties.setDataDirectory(dataDirectory);
        }

        // Disable ONLY_FULL_GROUP_BY, required since MariaDb v5.7.5 to allow under-constrained column values via group-by.
        embeddedDatabaseProperties.addArgument("--sql_mode='STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_AUTO_CREATE_USER,NO_ENGINE_SUBSTITUTION'");

        final long connectionsReservedForRoot = 1;
        final long maxAllowedPacketByteCount = (32L * ByteUtil.Unit.Binary.MEBIBYTES);
        embeddedDatabaseProperties.setMaxAllowedPacketByteCount(maxAllowedPacketByteCount);

        final long bytesPerConnection = DatabaseConfigurer.getBytesPerDatabaseConnection();
        final long overheadByteCount = (bytesPerConnection * maxDatabaseConnectionCount);
        final long bufferDatabaseMemory = (bitcoinVerdeDatabaseProperties.getMaxMemoryByteCount() - overheadByteCount);

        if (SystemUtil.isWindowsOperatingSystem()) {
            // MariaDb4j currently only supports 32 bit on Windows, so the log file and memory settings must be less than 2 GB...
            embeddedDatabaseProperties.setInnoDbBufferPoolByteCount(Math.min(ByteUtil.Unit.Binary.GIBIBYTES, bitcoinVerdeDatabaseProperties.getMaxMemoryByteCount()));
            embeddedDatabaseProperties.setQueryCacheByteCount(0L);
            embeddedDatabaseProperties.setMaxConnectionCount(maxDatabaseConnectionCount + connectionsReservedForRoot);
        }
        else {
            final Long logFileByteCount = DatabaseConfigurer.toNearestMegabyte(bitcoinVerdeDatabaseProperties.getLogFileByteCount());
            final Long logBufferByteCount = DatabaseConfigurer.toNearestMegabyte(Math.min((logFileByteCount / 4L), (bufferDatabaseMemory / 4L))); // 25% of the logFile size but no larger than 25% of the total buffer memory.
            final Long bufferPoolByteCount = DatabaseConfigurer.toNearestMegabyte(bufferDatabaseMemory - logBufferByteCount);
            final int bufferPoolInstanceCount;
            { // https://www.percona.com/blog/2020/08/13/how-many-innodb_buffer_pool_instances-do-you-need-in-mysql-8/
                final int segmentCount = (int) (bufferPoolByteCount / (256L * ByteUtil.Unit.Binary.MEBIBYTES));
                if (segmentCount < 1) { bufferPoolInstanceCount = 1; }
                else if (segmentCount > 32) { bufferPoolInstanceCount = 32; }
                else { bufferPoolInstanceCount = segmentCount; }
            }

            // TODO: Experiment with innodb_change_buffer_max_size and innodb_change_buffering for better IBD performance.

            embeddedDatabaseProperties.setInnoDbLogFileByteCount(logFileByteCount); // Redo Log file (on-disk)
            embeddedDatabaseProperties.setInnoDbLogBufferByteCount(logBufferByteCount); // Redo Log (in-memory)
            embeddedDatabaseProperties.setInnoDbBufferPoolByteCount(bufferPoolByteCount); // Innodb Dirty Page Cache
            embeddedDatabaseProperties.setInnoDbBufferPoolInstanceCount(bufferPoolInstanceCount); // Innodb Dirt Page Concurrency

            embeddedDatabaseProperties.setInnoDbFlushLogAtTransactionCommit(0); // Write directly to disk; database crashing may result in data corruption.
            embeddedDatabaseProperties.setInnoDbFlushMethod("O_DIRECT");

            embeddedDatabaseProperties.setInnoDbIoCapacity(5000L); // 2000 for SSDs/NVME, 400 for low-end SSD, 200 for HDD.  Higher encourages fewer dirty pages passively.
            embeddedDatabaseProperties.setInnoDbIoCapacityMax(10000L); // Higher facilitates active dirty-page flushing.
            embeddedDatabaseProperties.setInnoDbPageCleaners(bufferPoolInstanceCount);
            embeddedDatabaseProperties.setInnoDbMaxDirtyPagesPercent(0F); // Encourage no dirty buffers in order to facilitate faster writes.
            embeddedDatabaseProperties.setInnoDbMaxDirtyPagesPercentLowWaterMark(5F); // Encourage no dirty buffers in order to facilitate faster writes.
            embeddedDatabaseProperties.setInnoDbReadIoThreads(16);
            embeddedDatabaseProperties.setInnoDbWriteIoThreads(32);
            embeddedDatabaseProperties.setInnoDbLeastRecentlyUsedScanDepth(2048);
            embeddedDatabaseProperties.setMyisamSortBufferSize(4096); // Reduce per-connection memory allocation (only used for MyISAM DDL statements).

            embeddedDatabaseProperties.setQueryCacheByteCount(null); // Deprecated, removed in Mysql 8.
            embeddedDatabaseProperties.setMaxConnectionCount(maxDatabaseConnectionCount + connectionsReservedForRoot);

            // embeddedDatabaseProperties.enableSlowQueryLog("slow-query.log", 1L);
            // embeddedDatabaseProperties.enableGeneralQueryLog("query.log");
            embeddedDatabaseProperties.enablePerformanceSchema(false);

            // Experimental change to prevent query optimization deadlock, in theory caused in MariaDB 10.5.8.
            embeddedDatabaseProperties.addArgument("--optimizer_search_depth=0");
        }

        return embeddedDatabaseProperties;
    }

    protected DatabaseConfigurer() { }
}
