package com.softwareverde.bitcoin.server.module.spv;

import java.io.InputStream;

public class SpvResourceLoader {
    public static final String BOOTSTRAP_HEADERS            = "/bootstrap/headers.dat";
    public static final String INIT_SQL_SQLITE              = "/sql/spv/init_sqlite.sql";
    public static final String INIT_SQL_INDEXES_SQLITE      = "/sql/spv/init_indexes_sqlite.sql";
    public static final String INIT_SQL_METADATA_SQLITE     = "/sql/spv/init_metadata_sqlite.sql";

    public static InputStream getResource(final String resourcePath) {
        return SpvResourceLoader.class.getClassLoader().getResourceAsStream(resourcePath);
    }
}
