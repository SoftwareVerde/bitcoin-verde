package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.Util;

import java.util.Properties;

public class DatabasePropertiesLoader {
    public static DatabaseProperties loadDatabaseProperties(final String prefix, final Properties properties) {
        final String propertyPrefix = (prefix == null ? "" : (prefix + "."));
        final String rootPassword = properties.getProperty(propertyPrefix + "database.rootPassword", "d3d4a3d0533e3e83bc16db93414afd96");
        final String hostname = properties.getProperty(propertyPrefix + "database.hostname", "");
        final String username = properties.getProperty(propertyPrefix + "database.username", "root");
        final String password = properties.getProperty(propertyPrefix + "database.password", "");
        final String schema = (properties.getProperty(propertyPrefix + "database.schema", "bitcoin")).replaceAll("[^A-Za-z0-9_]", "");
        final Integer port = Util.parseInt(properties.getProperty(propertyPrefix + "database.port", "8336"));
        final String dataDirectory = properties.getProperty(propertyPrefix + "database.dataDirectory", "data");
        final Boolean useEmbeddedDatabase = Util.parseBool(properties.getProperty(propertyPrefix + "database.useEmbeddedDatabase", "1"));
        final Long maxMemoryByteCount = Util.parseLong(properties.getProperty(propertyPrefix + "database.maxMemoryByteCount", String.valueOf(2L * ByteUtil.Unit.Binary.GIBIBYTES)));
        final Long logFileByteCount = Util.parseLong(properties.getProperty(propertyPrefix + "database.logFileByteCount", String.valueOf(512 * ByteUtil.Unit.Binary.MEBIBYTES)));

        final DatabaseProperties databaseProperties = new DatabaseProperties();
        databaseProperties.setRootPassword(rootPassword);
        databaseProperties.setHostname(hostname);
        databaseProperties.setUsername(username);
        databaseProperties.setPassword(password);
        databaseProperties.setSchema(schema);
        databaseProperties.setPort(port);
        databaseProperties.setDataDirectory(dataDirectory);
        databaseProperties._useEmbeddedDatabase = useEmbeddedDatabase;
        databaseProperties._maxMemoryByteCount = maxMemoryByteCount;
        databaseProperties._logFileByteCount = logFileByteCount;
        return databaseProperties;
    }

    protected DatabasePropertiesLoader() { }
}
