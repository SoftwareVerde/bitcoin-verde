package com.softwareverde.bitcoin.server.module.spv;

import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.util.IoUtil;

import java.io.InputStream;

public class SpvResourceLoader {
    public static final String BOOTSTRAP_HEADERS            = "/bootstrap/headers.dat";
    public static final String INIT_SQL_SQLITE              = "/sql/spv/sqlite/init.sql";
    public static final Integer DATABASE_VERSION            = BitcoinConstants.DATABASE_VERSION;

    /**
     * Attempts to find the resource using different resource-loading entities.  The IoUtil method, this class's
     * classloader, and then this class.  If none of these yields a non-null input stream, null is returned.
     * @param resourcePath
     * @return
     */
    public static InputStream getResourceAsStream(final String resourcePath) {
        final InputStream ioUtilSteam = IoUtil.getResourceAsStream(resourcePath);
        if (ioUtilSteam != null) {
            return ioUtilSteam;
        }
        final InputStream classpathStream = SpvResourceLoader.class.getClassLoader().getResourceAsStream(resourcePath);
        if (classpathStream != null) {
            return classpathStream;
        }
        final InputStream classStream = SpvResourceLoader.class.getResourceAsStream(resourcePath);
        return classStream;
    }

    public static String getResource(final String resourcePath) {
        final InputStream inputStream = SpvResourceLoader.getResourceAsStream(resourcePath);
        if (inputStream == null) {
            return null;
        }
        return IoUtil.streamToString(inputStream);
    }
}
