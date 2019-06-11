package com.softwareverde.bitcoin.server.module.stratum.database;

import com.softwareverde.bitcoin.miner.pool.WorkerId;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.security.pbkdf2.Pbkdf2Key;
import com.softwareverde.util.Util;

public class WorkerDatabaseManager {
    protected final MysqlDatabaseConnection _databaseConnection;

    public WorkerDatabaseManager(final MysqlDatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    public WorkerId getWorkerId(final String username) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM workers WHERE username = ?")
                .setParameter(username)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return WorkerId.wrap(row.getLong("id"));
    }

    public WorkerId insertWorker(final String username, final String password) throws DatabaseException {
        final Pbkdf2Key pbkdf2Key = new Pbkdf2Key(password);

        final Long workerId = _databaseConnection.executeSql(
            new Query("INSERT INTO workers (username, password, salt, iterations) VALUES (?, ?, ?, ?)")
                .setParameter(username)
                .setParameter(pbkdf2Key.getKey())
                .setParameter(pbkdf2Key.getSalt())
                .setParameter(pbkdf2Key.getIterations())
        );

        return WorkerId.wrap(workerId);
    }

    public WorkerId authenticateWorker(final String username, final String password) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, password, salt, iterations FROM workers WHERE username = ?")
                .setParameter(username)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Long workerId = row.getLong("id");
        final ByteArray passwordKey = ByteArray.fromHexString(row.getString("password"));
        final ByteArray salt = ByteArray.fromHexString(row.getString("salt"));
        final Integer iterations = row.getInteger("iterations");
        final Integer keyBitCount = (passwordKey.getByteCount() * 8);

        final Pbkdf2Key providedPbkdf2Key = new Pbkdf2Key(password, iterations, salt, keyBitCount);
        final ByteArray providedKey = providedPbkdf2Key.getKey();

        if (! Util.areEqual(passwordKey, providedKey)) { return null; }

        return WorkerId.wrap(workerId);
    }
}
