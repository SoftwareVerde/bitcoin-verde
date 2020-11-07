package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.server.configuration.DatabaseProperties;
import com.softwareverde.bitcoin.server.main.DatabaseConfigurer;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.database.mysql.embedded.DatabaseCommandLineArguments;
import com.softwareverde.util.Util;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;

public class DatabaseConfigurerTests {
    @Test
    public void should_configure_linux_database_with_large_memory_available() {
        // Setup
        final Long systemByteCount = (64L * ByteUtil.Unit.Binary.GIBIBYTES);
        final long logFileByteCount = ByteUtil.Unit.Binary.GIBIBYTES;

        final DatabaseProperties databaseProperties = new DatabaseProperties() {
            @Override
            public Long getMaxMemoryByteCount() {
                return systemByteCount;
            }

            @Override
            public Long getLogFileByteCount() {
                return logFileByteCount;
            }
        };

        final DatabaseCommandLineArguments commandLineArguments = new DatabaseCommandLineArguments();
        final Integer maxDatabaseThreadCount = 5000; // Reasonably large number of connections. (MySQL allows up to 10k)
        final Long overhead = (maxDatabaseThreadCount * (11L * ByteUtil.Unit.Binary.MEBIBYTES)); // 3MB overhead + 8 max packet
        final Long usableSystemMemory = (systemByteCount - overhead);

        // Action
        DatabaseConfigurer.configureCommandLineArguments(commandLineArguments, maxDatabaseThreadCount, databaseProperties, null);

        final HashMap<String, String> arguments = new HashMap<String, String>();
        for (final String string : commandLineArguments.getArguments()) {
            final String[] splitArgument = string.split("=");
            if (splitArgument.length != 2) { continue; } // Ignore flags...

            arguments.put(splitArgument[0], splitArgument[1]);
        }

        // Assert
        final Long expectedBufferPoolByteCount = (usableSystemMemory - (logFileByteCount / 4L)); // Slightly more than 10GB due to 3MB per-connection overhead...
        Assert.assertEquals(Long.valueOf(logFileByteCount / 4L), Util.parseLong(arguments.get("--innodb_log_buffer_size")));
        Assert.assertEquals(expectedBufferPoolByteCount, Util.parseLong(arguments.get("--innodb_buffer_pool_size")));
    }

    @Test
    public void should_configure_linux_database_with_little_memory_available() {
        // Setup
        final long systemByteCount = ByteUtil.Unit.Binary.GIBIBYTES;
        final long logFileByteCount = (512L * ByteUtil.Unit.Binary.MEBIBYTES);

        final DatabaseProperties databaseProperties = new DatabaseProperties() {
            @Override
            public Long getMaxMemoryByteCount() {
                return systemByteCount;
            }

            @Override
            public Long getLogFileByteCount() {
                return logFileByteCount;
            }
        };

        final DatabaseCommandLineArguments commandLineArguments = new DatabaseCommandLineArguments();
        final Integer maxDatabaseThreadCount = 32; // Maximum supported by MySql...
        final Long overhead = (maxDatabaseThreadCount * (11L * ByteUtil.Unit.Binary.MEBIBYTES)); // 3MB overhead + 8 max packet
        final Long usableSystemMemory = (systemByteCount - overhead);

        // Action
        DatabaseConfigurer.configureCommandLineArguments(commandLineArguments, maxDatabaseThreadCount, databaseProperties, null);

        final HashMap<String, String> arguments = new HashMap<String, String>();
        for (final String string : commandLineArguments.getArguments()) {
            final String[] splitArgument = string.split("=");
            if (splitArgument.length != 2) { continue; } // Ignore flags...

            arguments.put(splitArgument[0], splitArgument[1]);
        }

        // Assert
        final Long bufferPoolByteCount = (usableSystemMemory - (logFileByteCount / 4L));
        Assert.assertEquals(Long.valueOf(logFileByteCount / 4L), Util.parseLong(arguments.get("--innodb_log_buffer_size")));
        Assert.assertEquals(bufferPoolByteCount, Util.parseLong(arguments.get("--innodb_buffer_pool_size")));
    }

    @Test
    public void should_configure_linux_database_with_little_non_aligned_size() {
        // Setup
        final long systemByteCount = (ByteUtil.Unit.Binary.GIBIBYTES + 1L);

        final DatabaseProperties databaseProperties = new DatabaseProperties() {
            @Override
            public Long getMaxMemoryByteCount() {
                return systemByteCount;
            }

            @Override
            public Long getLogFileByteCount() {
                return systemByteCount;
            }
        };

        final DatabaseCommandLineArguments commandLineArguments = new DatabaseCommandLineArguments();
        final Integer maxDatabaseThreadCount = 64;
        final Long overhead = (maxDatabaseThreadCount * (11L * ByteUtil.Unit.Binary.MEBIBYTES)); // 3MB overhead + 8 max packet
        final Long usableSystemMemory = (systemByteCount - overhead);

        // Action
        DatabaseConfigurer.configureCommandLineArguments(commandLineArguments, maxDatabaseThreadCount, databaseProperties, null);

        final HashMap<String, String> arguments = new HashMap<String, String>();
        for (final String string : commandLineArguments.getArguments()) {
            final String[] splitArgument = string.split("=");
            if (splitArgument.length != 2) { continue; } // Ignore flags...

            arguments.put(splitArgument[0], splitArgument[1]);
        }

        // Assert
        Assert.assertEquals(Long.valueOf(usableSystemMemory / 4L), Util.parseLong(arguments.get("--innodb_log_buffer_size")));
        Assert.assertEquals(Long.valueOf(usableSystemMemory * 3L / 4L), Util.parseLong(arguments.get("--innodb_buffer_pool_size")));
    }
}
