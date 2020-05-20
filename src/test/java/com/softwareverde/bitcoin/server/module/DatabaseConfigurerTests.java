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
        final Long systemByteCount = (64L * ByteUtil.Unit.GIGABYTES);

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
        final Integer maxDatabaseThreadCount = 100000; // Maximum supported by MySql...

        // Action
        DatabaseConfigurer.configureCommandLineArguments(commandLineArguments, maxDatabaseThreadCount, databaseProperties, null);

        final HashMap<String, String> arguments = new HashMap<String, String>();
        for (final String string : commandLineArguments.getArguments()) {
            final String[] splitArgument = string.split("=");
            if (splitArgument.length != 2) { continue; } // Ignore flags...

            arguments.put(splitArgument[0], splitArgument[1]);
        }

        // Assert
        Assert.assertEquals(Util.parseLong(arguments.get("--innodb_log_buffer_size")), ByteUtil.Unit.GIGABYTES);
        Assert.assertEquals(Util.parseLong(arguments.get("--innodb_buffer_pool_size")), Long.valueOf(63L * ByteUtil.Unit.GIGABYTES));
    }

    @Test
    public void should_configure_linux_database_with_little_memory_available() {
        // Setup
        final Long systemByteCount = (1L * ByteUtil.Unit.GIGABYTES);

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
        final Integer maxDatabaseThreadCount = 100000; // Maximum supported by MySql...

        // Action
        DatabaseConfigurer.configureCommandLineArguments(commandLineArguments, maxDatabaseThreadCount, databaseProperties, null);

        final HashMap<String, String> arguments = new HashMap<String, String>();
        for (final String string : commandLineArguments.getArguments()) {
            final String[] splitArgument = string.split("=");
            if (splitArgument.length != 2) { continue; } // Ignore flags...

            arguments.put(splitArgument[0], splitArgument[1]);
        }

        // Assert
        Assert.assertEquals(Util.parseLong(arguments.get("--innodb_log_buffer_size")), Long.valueOf(systemByteCount / 4L));
        Assert.assertEquals(Util.parseLong(arguments.get("--innodb_buffer_pool_size")), Long.valueOf(systemByteCount * 3L / 4L));
    }

    @Test
    public void should_configure_linux_database_with_little_non_aligned_size() {
        // Setup
        final Long systemByteCount = (1L * ByteUtil.Unit.GIGABYTES) + 1L;

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
        final Integer maxDatabaseThreadCount = 100000; // Maximum supported by MySql...

        // Action
        DatabaseConfigurer.configureCommandLineArguments(commandLineArguments, maxDatabaseThreadCount, databaseProperties, null);

        final HashMap<String, String> arguments = new HashMap<String, String>();
        for (final String string : commandLineArguments.getArguments()) {
            final String[] splitArgument = string.split("=");
            if (splitArgument.length != 2) { continue; } // Ignore flags...

            arguments.put(splitArgument[0], splitArgument[1]);
        }

        // Assert
        Assert.assertEquals(Util.parseLong(arguments.get("--innodb_log_buffer_size")), Long.valueOf(systemByteCount / 4L));
        Assert.assertEquals(Util.parseLong(arguments.get("--innodb_buffer_pool_size")), Long.valueOf(systemByteCount * 3L / 4L));
    }
}
