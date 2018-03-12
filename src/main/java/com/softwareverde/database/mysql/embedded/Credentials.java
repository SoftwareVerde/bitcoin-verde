package com.softwareverde.database.mysql.embedded;

public class Credentials {
    public final String username;
    public final String password;
    public final String schema;

    public Credentials(final String username, final String password, final String schema) {
        this.username = username;
        this.password = password;
        this.schema = schema;
    }
}
