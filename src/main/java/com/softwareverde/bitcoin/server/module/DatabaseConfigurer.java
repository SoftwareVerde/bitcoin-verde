package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.database.mysql.embedded.DatabaseCommandLineArguments;
import com.softwareverde.util.ByteUtil;

public class DatabaseConfigurer {
    public static void configureCommandLineArguments(final DatabaseCommandLineArguments commandLineArguments, final Configuration.ServerProperties serverProperties) {
        final Integer maxDatabaseThreadCount = Math.max(512, (serverProperties.getMaxPeerCount() * 8));

        commandLineArguments.setInnoDbBufferPoolByteCount(serverProperties.getMaxMemoryByteCount());
        commandLineArguments.setInnoDbBufferPoolInstanceCount(4);

        commandLineArguments.setInnoDbLogBufferByteCount(ByteUtil.Unit.GIGABYTES);

        commandLineArguments.addArgument("--innodb-flush-log-at-trx-commit=0");
        commandLineArguments.addArgument("--innodb-flush-method=O_DIRECT");

        commandLineArguments.setInnoDbLogFileByteCount(32 * ByteUtil.Unit.GIGABYTES);

        commandLineArguments.setQueryCacheByteCount(0L);

        commandLineArguments.setMaxAllowedPacketByteCount(128 * ByteUtil.Unit.MEGABYTES);

        commandLineArguments.addArgument("--max-connections=" + maxDatabaseThreadCount);
        commandLineArguments.addArgument("--innodb-read-io-threads=8");
        commandLineArguments.addArgument("--innodb-write-io-threads=8");

        commandLineArguments.enableSlowQueryLog("slow-query.log", 1L);
        // commandLineArguments.addArgument("--performance_schema");
        commandLineArguments.addArgument("--general_log_file=query.log");
        commandLineArguments.addArgument("--general_log=1");
    }
}
