package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.server.configuration.BitcoinVerdeDatabaseProperties;
import com.softwareverde.bitcoin.server.main.DatabaseConfigurer;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.database.mysql.embedded.properties.EmbeddedDatabaseProperties;
import com.softwareverde.util.Util;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;

public class DatabaseConfigurerTests extends UnitTest {
    @Before
    public void before() throws Exception {
        super.before();
    }

    @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_configure_linux_database_with_large_memory_available() {
        // Setup
        final long systemByteCount = (64L * ByteUtil.Unit.Binary.GIBIBYTES);
        final long logFileByteCount = ByteUtil.Unit.Binary.GIBIBYTES;

        final BitcoinVerdeDatabaseProperties databaseProperties = new BitcoinVerdeDatabaseProperties() {
            @Override
            public Long getMaxMemoryByteCount() {
                return systemByteCount;
            }

            @Override
            public Long getLogFileByteCount() {
                return logFileByteCount;
            }
        };

        final int maxDatabaseThreadCount = 5000; // Reasonably large number of connections. (MySQL allows up to 10k)
        final long overhead = (maxDatabaseThreadCount * (11L * ByteUtil.Unit.Binary.MEBIBYTES)); // 3MB overhead + 8 max packet
        final long usableSystemMemory = (systemByteCount - overhead);

        // Action
        final EmbeddedDatabaseProperties databaseConfiguration = DatabaseConfigurer.configureDatabase(maxDatabaseThreadCount, databaseProperties);

        final HashMap<String, String> arguments = new HashMap<String, String>();
        for (final String string : databaseConfiguration.getCommandlineArguments()) {
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

        final BitcoinVerdeDatabaseProperties databaseProperties = new BitcoinVerdeDatabaseProperties() {
            @Override
            public Long getMaxMemoryByteCount() {
                return systemByteCount;
            }

            @Override
            public Long getLogFileByteCount() {
                return logFileByteCount;
            }
        };

        final int maxDatabaseThreadCount = 32; // Maximum supported by MySql...
        final long overhead = (maxDatabaseThreadCount * (11L * ByteUtil.Unit.Binary.MEBIBYTES)); // 3MB overhead + 8 max packet
        final long usableSystemMemory = (systemByteCount - overhead);

        // Action
        final EmbeddedDatabaseProperties databaseConfiguration = DatabaseConfigurer.configureDatabase(maxDatabaseThreadCount, databaseProperties);

        final HashMap<String, String> arguments = new HashMap<String, String>();
        for (final String string : databaseConfiguration.getCommandlineArguments()) {
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

        final BitcoinVerdeDatabaseProperties databaseProperties = new BitcoinVerdeDatabaseProperties() {
            @Override
            public Long getMaxMemoryByteCount() {
                return systemByteCount;
            }

            @Override
            public Long getLogFileByteCount() {
                return systemByteCount;
            }
        };

        final int maxDatabaseThreadCount = 64;
        final long overhead = (maxDatabaseThreadCount * (11L * ByteUtil.Unit.Binary.MEBIBYTES)); // 3MB overhead + 8 max packet
        final long usableSystemMemory = (systemByteCount - overhead);

        // Action
        final EmbeddedDatabaseProperties databaseConfiguration = DatabaseConfigurer.configureDatabase(maxDatabaseThreadCount, databaseProperties);

        final HashMap<String, String> arguments = new HashMap<String, String>();
        for (final String string : databaseConfiguration.getCommandlineArguments()) {
            final String[] splitArgument = string.split("=");
            if (splitArgument.length != 2) { continue; } // Ignore flags...

            arguments.put(splitArgument[0], splitArgument[1]);
        }

        // Assert
        Assert.assertEquals(Long.valueOf(usableSystemMemory / 4L), Util.parseLong(arguments.get("--innodb_log_buffer_size")));
        Assert.assertEquals(Long.valueOf(usableSystemMemory * 3L / 4L), Util.parseLong(arguments.get("--innodb_buffer_pool_size")));
    }
}
