package com.softwareverde.bitcoin.server.module.stratum.database;

import com.softwareverde.bitcoin.miner.pool.AccountId;
import com.softwareverde.bitcoin.miner.pool.WorkerId;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.pbkdf2.Pbkdf2Key;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

public class WorkerDatabaseManager {
    protected static final Integer WORKER_PASSWORD_ITERATIONS = 8; // Authenticating workers happens much more frequently than account authentications...

    protected final DatabaseConnection _databaseConnection;

    public WorkerDatabaseManager(final DatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    public WorkerId createWorker(final AccountId accountId, final String username, final String password) throws DatabaseException {
        final Pbkdf2Key pbkdf2Key = new Pbkdf2Key(password, WORKER_PASSWORD_ITERATIONS, Pbkdf2Key.generateRandomSalt(), Pbkdf2Key.DEFAULT_KEY_BIT_COUNT);
        final ByteArray keyByteArray = pbkdf2Key.getKey();
        final ByteArray saltByteArray = pbkdf2Key.getSalt();

        final Long workerId = _databaseConnection.executeSql(
            new Query("INSERT INTO workers (account_id, username, password, salt, iterations) VALUES (?, ?, ?, ?, ?)")
                .setParameter(accountId)
                .setParameter(username)
                .setParameter(keyByteArray.toString())
                .setParameter(saltByteArray.toString())
                .setParameter(pbkdf2Key.getIterations())
        );

        return WorkerId.wrap(workerId);
    }

    public void restoreWorker(final WorkerId workerId, final String password) throws DatabaseException {
        final Pbkdf2Key pbkdf2Key = new Pbkdf2Key(password, WORKER_PASSWORD_ITERATIONS, Pbkdf2Key.generateRandomSalt(), Pbkdf2Key.DEFAULT_KEY_BIT_COUNT);
        final ByteArray keyByteArray = pbkdf2Key.getKey();
        final ByteArray saltByteArray = pbkdf2Key.getSalt();

        _databaseConnection.executeSql(
            new Query("UPDATE workers SET password = ?, salt = ?, iterations = ?, was_deleted = 0 WHERE id = ?")
                .setParameter(keyByteArray.toString())
                .setParameter(saltByteArray.toString())
                .setParameter(pbkdf2Key.getIterations())
                .setParameter(workerId)
        );
    }

    public WorkerId getWorkerId(final String username) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM workers WHERE username = ?")
                .setParameter(username)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Long workerId = row.getLong("id");
        return WorkerId.wrap(workerId);
    }

    public void recordFoundBlock(final Sha256Hash blockHash, final WorkerId workerId) throws DatabaseException {
        final SystemTime systemTime = new SystemTime();
        final Long timestampMs = systemTime.getCurrentTimeInMilliSeconds();

        _databaseConnection.executeSql(
            new Query("INSERT INTO found_blocks (hash, worker_id, timestamp) VALUES (?, ?, ?)")
                .setParameter(blockHash)
                .setParameter(workerId)
                .setParameter(timestampMs)
        );
    }

    public Boolean hasWorkedBeenDeleted(final WorkerId workerId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, was_deleted FROM workers WHERE id = ?")
                .setParameter(workerId)
        );
        if (rows.isEmpty()) { return false; }

        final Row row = rows.get(0);
        return row.getBoolean("was_deleted");
    }

    public AccountId getWorkerAccountId(final WorkerId workerId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, account_id FROM workers WHERE id = ?")
                .setParameter(workerId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Long accountId = row.getLong("account_id");
        return AccountId.wrap(accountId);
    }

    public void destroyWorker(final WorkerId workerId) throws DatabaseException {
        _databaseConnection.executeSql(
            new Query("DELETE FROM workers WHERE id = ?")
                .setParameter(workerId)
        );
    }

    public void deleteWorker(final WorkerId workerId) throws DatabaseException {
        _databaseConnection.executeSql(
            new Query("UPDATE workers SET was_deleted = 1 WHERE id = ?")
                .setParameter(workerId)
        );
    }

    public List<WorkerId> getWorkerIds(final AccountId accountId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM workers WHERE account_id = ? AND was_deleted = 0")
                .setParameter(accountId)
        );

        final ImmutableListBuilder<WorkerId> workerIds = new ImmutableListBuilder<>(rows.size());
        for (final Row row : rows) {
            final WorkerId workerId = WorkerId.wrap(row.getLong("id"));
            if (workerId == null) { continue; }
            workerIds.add(workerId);
        }
        return workerIds.build();
    }

    public String getWorkerUsername(final WorkerId workerId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, username FROM workers WHERE id = ?")
                .setParameter(workerId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return row.getString("username");
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

    public void addWorkerShare(final WorkerId workerId, final Long difficulty, final Long blockHeight, final Sha256Hash blockHash) throws DatabaseException {
        final SystemTime systemTime = new SystemTime();

        _databaseConnection.executeSql(
            new Query("INSERT IGNORE INTO worker_shares (worker_id, difficulty, block_height, hash, timestamp) VALUES (?, ?, ?, ?, ?)")
                .setParameter(workerId)
                .setParameter(difficulty)
                .setParameter(blockHeight)
                .setParameter(blockHash)
                .setParameter(systemTime.getCurrentTimeInSeconds())
        );
    }

    public void addWorkerShares(final List<WorkerShare> workerShares) throws DatabaseException {
        if (workerShares.isEmpty()) { return; }

        final BatchedInsertQuery query = new BatchedInsertQuery("INSERT IGNORE INTO worker_shares (worker_id, difficulty, block_height, hash, timestamp) VALUES (?, ?, ?, ?, ?)");
        for (final WorkerShare workerShare : workerShares) {
            query.setParameter(workerShare.workerId);
            query.setParameter(workerShare.shareDifficulty);
            query.setParameter(workerShare.blockHeight);
            query.setParameter(workerShare.blockHash);
            query.setParameter(workerShare.timestamp);
        }

        _databaseConnection.executeSql(query);
    }

    public Long getWorkerShareCount(final WorkerId workerId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT COUNT(*) AS shares_count FROM worker_shares WHERE worker_id = ?")
                .setParameter(workerId)
        );
        if (rows.isEmpty()) { return 0L; }

        final Row row = rows.get(0);
        return row.getLong("shares_count");
    }

    public Long getWorkerShareCount(final WorkerId workerId, final Long blockHeight) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT COUNT(*) AS shares_count FROM worker_shares WHERE worker_id = ? AND block_height = ?")
                .setParameter(workerId)
                .setParameter(blockHeight)
        );
        if (rows.isEmpty()) { return 0L; }

        final Row row = rows.get(0);
        return row.getLong("shares_count");
    }

    /**
     * Returns the normalized share difficulty for the provided worker.
     */
    public Long getTotalWorkerShares(final WorkerId workerId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT SUM(difficulty) AS shares_count FROM worker_shares WHERE worker_id = ?")
                .setParameter(workerId)
        );
        if (rows.isEmpty()) { return 0L; }

        final Row row = rows.get(0);
        return Util.coalesce(row.getLong("shares_count"));
    }

    /**
     * Returns the normalized share difficulty for the provided worker for the block height.
     */
    public Long getTotalWorkerShares(final WorkerId workerId, final Long blockHeight) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT SUM(difficulty) AS shares_count FROM worker_shares WHERE worker_id = ? AND block_height = ?")
                .setParameter(workerId)
                .setParameter(blockHeight)
        );
        if (rows.isEmpty()) { return 0L; }

        final Row row = rows.get(0);
        return Util.coalesce(row.getLong("shares_count"));
    }
}
