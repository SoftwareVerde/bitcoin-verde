package com.softwareverde.bitcoin.server.module.node.database.transaction.pending;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.database.query.ValueExtractor;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.pending.PendingTransaction;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.pending.PendingTransactionId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.util.type.time.SystemTime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PendingTransactionDatabaseManager {
    public static final Long MAX_ORPHANED_TRANSACTION_AGE_IN_SECONDS = (60 * 60L); // 1 Hour...

    public static final ReentrantReadWriteLock.ReadLock READ_LOCK;
    public static final ReentrantReadWriteLock.WriteLock WRITE_LOCK;
    static {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        READ_LOCK = readWriteLock.readLock();
        WRITE_LOCK = readWriteLock.writeLock();
    }

    protected final SystemTime _systemTime = new SystemTime();
    protected final DatabaseManager _databaseManager;
    protected final TransactionInflater _transactionInflater;
    protected final TransactionDeflater _transactionDeflater;

    protected PendingTransactionId _getPendingTransactionId(final Sha256Hash transactionHash) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM pending_transactions WHERE hash = ?")
                .setParameter(transactionHash)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return PendingTransactionId.wrap(row.getLong("id"));
    }

    protected PendingTransactionId _storePendingTransaction(final Sha256Hash transactionHash) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Long currentTimestamp = _systemTime.getCurrentTimeInSeconds();
        final Long priority = currentTimestamp;
        final Long pendingTransactionId = databaseConnection.executeSql(
            new Query("INSERT IGNORE INTO pending_transactions (hash, timestamp, priority) VALUES (?, ?, ?)")
                .setParameter(transactionHash)
                .setParameter(currentTimestamp)
                .setParameter(priority)
        );

        if (pendingTransactionId == 0) {
            // The insert was ignored, so return the existing row.  This logic is necessary to prevent a race condition due to PendingTransactionDatabaseManager not locking...
            return _getPendingTransactionId(transactionHash);
        }

        return PendingTransactionId.wrap(pendingTransactionId);
    }

    protected void _insertPendingTransactionData(final PendingTransactionId pendingTransactionId, final ByteArray transactionData) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        databaseConnection.executeSql(
            new Query("INSERT IGNORE INTO pending_transaction_data (pending_transaction_id, data) VALUES (?, ?)")
                .setParameter(pendingTransactionId)
                .setParameter(transactionData.getBytes())
        );
    }

    protected List<PendingTransactionId> _storeTransactionHashes(final List<Sha256Hash> transactionHashes) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Long currentTimestamp = _systemTime.getCurrentTimeInSeconds();
        final Long priority = currentTimestamp;
        final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT IGNORE INTO pending_transactions (hash, timestamp, priority) VALUES (?, ?, ?)");
        for (final Sha256Hash transactionHash : transactionHashes) {
            batchedInsertQuery.setParameter(transactionHash);
            batchedInsertQuery.setParameter(currentTimestamp);
            batchedInsertQuery.setParameter(priority);
        }

        final Long firstPendingTransactionId = databaseConnection.executeSql(batchedInsertQuery);
        final Integer newRowCount = databaseConnection.getRowsAffectedCount();

        final ImmutableListBuilder<PendingTransactionId> pendingTransactionIds = new ImmutableListBuilder<PendingTransactionId>(newRowCount);
        for (int i = 0; i < newRowCount; ++i) {
            pendingTransactionIds.add(PendingTransactionId.wrap(firstPendingTransactionId + i));
        }

        return pendingTransactionIds.build();
    }

    protected Map<NodeId, ? extends List<PendingTransactionId>> _selectIncompletePendingTransactions(final List<NodeId> connectedNodeIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Long minSecondsBetweenDownloadAttempts = 5L;
        final Long currentTimestamp = _systemTime.getCurrentTimeInSeconds();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT node_transactions_inventory.node_id, pending_transactions.id AS pending_transaction_id FROM pending_transactions LEFT OUTER JOIN pending_transaction_data ON pending_transactions.id = pending_transaction_data.pending_transaction_id INNER JOIN node_transactions_inventory ON node_transactions_inventory.hash = pending_transactions.hash WHERE (pending_transaction_data.id IS NULL) AND ( (? - COALESCE(last_download_attempt_timestamp, 0)) > ? ) AND node_transactions_inventory.node_id IN (?) ORDER BY pending_transactions.priority ASC, pending_transactions.id ASC LIMIT 1024")
                .setParameter(currentTimestamp)
                .setParameter(minSecondsBetweenDownloadAttempts)
                .setInClauseParameters(connectedNodeIds, ValueExtractor.IDENTIFIER)
        );

        final HashMap<NodeId, MutableList<PendingTransactionId>> downloadPlan = new HashMap<NodeId, MutableList<PendingTransactionId>>();
        for (final Row row : rows) {
            final NodeId nodeId = NodeId.wrap(row.getLong("node_id"));
            final PendingTransactionId pendingTransactionId = PendingTransactionId.wrap(row.getLong("pending_transaction_id"));
            if (! downloadPlan.containsKey(nodeId)) {
                downloadPlan.put(nodeId, new MutableList<PendingTransactionId>());
            }

            final MutableList<PendingTransactionId> list = downloadPlan.get(nodeId);
            list.add(pendingTransactionId);
        }
        return downloadPlan;
    }

    protected List<PendingTransactionId> _selectCandidatePendingTransactionIds() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT pending_transactions.id, pending_transactions.hash FROM pending_transactions INNER JOIN pending_transaction_data ON pending_transaction_data.pending_transaction_id = pending_transactions.id WHERE NOT EXISTS (SELECT * FROM pending_transactions_dependent_transactions LEFT OUTER JOIN transactions ON transactions.hash = pending_transactions_dependent_transactions.hash WHERE (transactions.id IS NULL) AND (pending_transactions_dependent_transactions.pending_transaction_id = pending_transactions.id))")
        );

        final ImmutableListBuilder<PendingTransactionId> pendingTransactionIds = new ImmutableListBuilder<PendingTransactionId>(rows.size());
        for (final Row row : rows) {
            final PendingTransactionId pendingTransactionId = PendingTransactionId.wrap(row.getLong("id"));
            pendingTransactionIds.add(pendingTransactionId);
        }
        return pendingTransactionIds.build();
    }

    protected Sha256Hash _getPendingTransactionHash(final PendingTransactionId pendingTransactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, hash FROM pending_transactions WHERE id = ?")
                .setParameter(pendingTransactionId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return Sha256Hash.copyOf(row.getBytes("hash"));
    }

    protected void _incrementFailedDownloadCount(final PendingTransactionId pendingTransactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        databaseConnection.executeSql(
            new Query("UPDATE pending_transactions SET failed_download_count = failed_download_count + 1, priority = priority + 60 WHERE id = ?")
                .setParameter(pendingTransactionId)
        );
    }

    protected void _updateLastDownloadAttemptTime(final PendingTransactionId pendingTransactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Long currentTimestamp = _systemTime.getCurrentTimeInSeconds();
        databaseConnection.executeSql(
            new Query("UPDATE pending_transactions SET last_download_attempt_timestamp = ? WHERE id = ?")
                .setParameter(currentTimestamp)
                .setParameter(pendingTransactionId)
        );
    }

    protected void _setPriority(final PendingTransactionId pendingTransactionId, final Long priority) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        databaseConnection.executeSql(
            new Query("UPDATE pending_transactions SET priority = ? WHERE id = ?")
                .setParameter(priority)
                .setParameter(pendingTransactionId)
        );
    }

    protected void _deletePendingTransaction(final PendingTransactionId pendingTransactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Sha256Hash pendingTransactionHash = _getPendingTransactionHash(pendingTransactionId);

        databaseConnection.executeSql(
            new Query("DELETE FROM pending_transactions WHERE id = ?")
                .setParameter(pendingTransactionId)
        );

        databaseConnection.executeSql(
            new Query("DELETE FROM node_transactions_inventory WHERE hash = ?")
                .setParameter(pendingTransactionHash)
        );
    }

    protected void _purgeFailedPendingTransactions(final Integer maxFailedDownloadCount) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT pending_transactions.id FROM pending_transactions LEFT OUTER JOIN pending_transaction_data ON (pending_transactions.id = pending_transaction_data.pending_transaction_id) WHERE pending_transactions.failed_download_count > ? AND pending_transaction_data.id IS NULL")
                .setParameter(maxFailedDownloadCount)
        );

        final MutableList<PendingTransactionId> pendingTransactionIds = new MutableList<PendingTransactionId>(rows.size());
        for (final Row row : rows) {
            final PendingTransactionId pendingTransactionId = PendingTransactionId.wrap(row.getLong("id"));
            Logger.debug("Deleting Failed Pending Transaction: " + pendingTransactionId);
            pendingTransactionIds.add(pendingTransactionId);
        }

        _deletePendingTransactions(pendingTransactionIds);
    }

    protected void _deletePendingTransactions(final List<PendingTransactionId> pendingTransactionIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        if (pendingTransactionIds.isEmpty()) { return; }

        final HashSet<Sha256Hash> pendingTransactionHashes = new HashSet<Sha256Hash>(pendingTransactionIds.getCount());
        for (final PendingTransactionId pendingTransactionId : pendingTransactionIds) {
            final Sha256Hash pendingTransactionHash = _getPendingTransactionHash(pendingTransactionId);
            pendingTransactionHashes.add(pendingTransactionHash);
        }

        databaseConnection.executeSql(
            new Query("DELETE FROM pending_transactions WHERE id IN (?)")
                .setInClauseParameters(pendingTransactionIds, ValueExtractor.IDENTIFIER)
        );

        databaseConnection.executeSql(
            new Query("DELETE FROM node_transactions_inventory WHERE hash IN (?)")
                .setInClauseParameters(pendingTransactionHashes, ValueExtractor.SHA256_HASH)
        );
    }

    protected void _purgeExpiredOrphanedTransactions() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final Long minimumTimestamp = (_systemTime.getCurrentTimeInSeconds() - MAX_ORPHANED_TRANSACTION_AGE_IN_SECONDS);
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT pending_transactions.id FROM pending_transactions LEFT OUTER JOIN transactions ON transactions.hash = pending_transactions.hash WHERE (transactions.id IS NOT NULL) OR (pending_transactions.timestamp < ?)")
                .setParameter(minimumTimestamp)
        );
        final MutableList<PendingTransactionId> pendingTransactionIds = new MutableList<PendingTransactionId>(rows.size());
        for (final Row row : rows) {
            final PendingTransactionId pendingTransactionId = PendingTransactionId.wrap(row.getLong("id"));
            pendingTransactionIds.add(pendingTransactionId);
        }

        _deletePendingTransactions(pendingTransactionIds);
    }

    protected Boolean _hasTransactionData(final PendingTransactionId pendingTransactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM pending_transaction_data WHERE pending_transaction_id = ?")
                .setParameter(pendingTransactionId)
        );
        return (rows.size() > 0);
    }

    protected ByteArray _getTransactionData(final PendingTransactionId pendingTransactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, data FROM pending_transaction_data WHERE pending_transaction_id = ?")
                .setParameter(pendingTransactionId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return MutableByteArray.wrap(row.getBytes("data"));
    }

    protected Transaction _getTransaction(final PendingTransactionId pendingTransactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT pending_transactions.id, pending_transactions.hash, pending_transaction_data.data FROM pending_transactions INNER JOIN pending_transaction_data ON pending_transactions.id = pending_transaction_data.pending_transaction_id WHERE pending_transactions.id = ?")
                .setParameter(pendingTransactionId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Sha256Hash transactionHash = Sha256Hash.copyOf(row.getBytes("hash"));
        final ByteArray transactionData = MutableByteArray.wrap(row.getBytes("data"));

        final Transaction transaction = _transactionInflater.fromBytes(transactionData);
        if (transaction == null) {
            _deletePendingTransaction(pendingTransactionId);
            Logger.warn("Error inflating pending transaction: " + transactionHash + " " + transactionData);
        }

        return transaction;
    }

    protected PendingTransaction _getPendingTransaction(final PendingTransactionId pendingTransactionId, final Boolean includeDataIfAvailable) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, hash FROM pending_transactions WHERE id = ?")
                .setParameter(pendingTransactionId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Sha256Hash transactionHash = Sha256Hash.copyOf(row.getBytes("hash"));
        final ByteArray transactionData;
        {
            if (includeDataIfAvailable) {
                transactionData = _getTransactionData(pendingTransactionId);
            }
            else {
                transactionData = null;
            }
        }

        return new PendingTransaction(transactionHash, transactionData);
    }

    protected void _updateTransactionDependencies(final Transaction transaction) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final PendingTransactionId pendingTransactionId = _getPendingTransactionId(transaction.getHash());
        if (pendingTransactionId == null) { return; }

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        final HashSet<Sha256Hash> requiredTransactionHashes = new HashSet<Sha256Hash>(transactionInputs.getCount());
        for (final TransactionInput transactionInput : transactionInputs) {
            final Sha256Hash transactionHash = transactionInput.getPreviousOutputTransactionHash();
            requiredTransactionHashes.add(transactionHash);
        }
        if (requiredTransactionHashes.isEmpty()) { return; }

        final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT IGNORE INTO pending_transactions_dependent_transactions (pending_transaction_id, hash) VALUES (?, ?)");
        for (final Sha256Hash transactionHash : requiredTransactionHashes) {
            batchedInsertQuery.setParameter(pendingTransactionId);
            batchedInsertQuery.setParameter(transactionHash);
        }
        databaseConnection.executeSql(batchedInsertQuery);
    }

    public PendingTransactionDatabaseManager(final DatabaseManager databaseManager) {
        _databaseManager = databaseManager;
        _transactionInflater = new TransactionInflater();
        _transactionDeflater = new TransactionDeflater();
    }

    public PendingTransactionDatabaseManager(final DatabaseManager databaseManager, final TransactionInflater transactionInflater, final TransactionDeflater transactionDeflater) {
        _databaseManager = databaseManager;
        _transactionInflater = transactionInflater;
        _transactionDeflater = transactionDeflater;
    }

    public PendingTransactionId getPendingTransactionId(final Sha256Hash transactionHash) throws DatabaseException {
        try {
            READ_LOCK.lock();

            return _getPendingTransactionId(transactionHash);

        }
        finally {
            READ_LOCK.unlock();
        }
    }

    public Boolean hasTransactionData(final PendingTransactionId pendingTransactionId) throws DatabaseException {
        try {
            READ_LOCK.lock();

            return _hasTransactionData(pendingTransactionId);

        }
        finally {
            READ_LOCK.unlock();
        }
    }

    public Boolean pendingTransactionExists(final Sha256Hash transactionHash) throws DatabaseException {
        try {
            READ_LOCK.lock();

            final PendingTransactionId pendingTransactionId = _getPendingTransactionId(transactionHash);
            return (pendingTransactionId != null);

        }
        finally {
            READ_LOCK.unlock();
        }
    }

    public PendingTransactionId insertTransactionHash(final Sha256Hash transactionHash) throws DatabaseException {
        try {
            WRITE_LOCK.lock();

            return _storePendingTransaction(transactionHash);

        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public PendingTransactionId storeTransactionHash(final Sha256Hash transactionHash) throws DatabaseException {
        try {
            WRITE_LOCK.lock();

            final PendingTransactionId existingPendingTransactionId = _getPendingTransactionId(transactionHash);
            if (existingPendingTransactionId != null) { return existingPendingTransactionId; }

            return _storePendingTransaction(transactionHash);

        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    /**
     * Inserts Transaction Hashes into the pending_transactions table that do not already exist.
     *  NOTE: Since existing Transaction Hashes are ignored, the returned Ids are not a one-to-one mapping of the provided hashes, unlike most other DatabaseManagers.
     */
    public List<PendingTransactionId> storeTransactionHashes(final List<Sha256Hash> transactionHashes) throws DatabaseException {
        try {
            WRITE_LOCK.lock();
            return _storeTransactionHashes(transactionHashes);
        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public PendingTransactionId storeTransaction(final Transaction transaction) throws DatabaseException {
        try {
            WRITE_LOCK.lock();

            final Sha256Hash transactionHash = transaction.getHash();

            final PendingTransactionId pendingTransactionId;
            {
                final PendingTransactionId existingPendingTransactionId = _getPendingTransactionId(transactionHash);
                if (existingPendingTransactionId != null) {
                    pendingTransactionId = existingPendingTransactionId;
                }
                else {
                    pendingTransactionId = _storePendingTransaction(transactionHash);
                }
            }

            _insertPendingTransactionData(pendingTransactionId, _transactionDeflater.toBytes(transaction));

            _updateTransactionDependencies(transaction);

            return pendingTransactionId;

        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public Map<NodeId, ? extends List<PendingTransactionId>> selectIncompletePendingTransactions(final List<NodeId> connectedNodeIds) throws DatabaseException {
        try {
            READ_LOCK.lock();
            return _selectIncompletePendingTransactions(connectedNodeIds);
        }
        finally {
            READ_LOCK.unlock();
        }
    }

    /**
     * Returns a list of PendingTransactionIds that may be processed (i.e. their previous Outputs have been processed).
     */
    public List<PendingTransactionId> selectCandidatePendingTransactionIds() throws DatabaseException {
        try {
            READ_LOCK.lock();
            return _selectCandidatePendingTransactionIds();
        }
        finally {
            READ_LOCK.unlock();
        }
    }

    public Transaction getPendingTransaction(final PendingTransactionId pendingTransactionId) throws DatabaseException {
        try {
            READ_LOCK.lock();
            return _getTransaction(pendingTransactionId);
        }
        finally {
            READ_LOCK.unlock();
        }
    }

    public Sha256Hash getPendingTransactionHash(final PendingTransactionId pendingTransactionId) throws DatabaseException {
        try {
            READ_LOCK.lock();
            return _getPendingTransactionHash(pendingTransactionId);
        }
        finally {
            READ_LOCK.unlock();
        }
    }

    public void incrementFailedDownloadCount(final PendingTransactionId pendingTransactionId) throws DatabaseException {
        try {
            WRITE_LOCK.lock();
            _incrementFailedDownloadCount(pendingTransactionId);
        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public void updateLastDownloadAttemptTime(final PendingTransactionId pendingTransactionId) throws DatabaseException {
        try {
            WRITE_LOCK.lock();
            _updateLastDownloadAttemptTime(pendingTransactionId);
        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public void setPriority(final PendingTransactionId pendingTransactionId, final Long priority) throws DatabaseException {
        try {
            WRITE_LOCK.lock();
            _setPriority(pendingTransactionId, priority);
        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public void purgeFailedPendingTransactions(final Integer maxFailedDownloadCount) throws DatabaseException {
        try {
            WRITE_LOCK.lock();
            _purgeFailedPendingTransactions(maxFailedDownloadCount);
        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public void purgeExpiredOrphanedTransactions() throws DatabaseException {
        try {
            WRITE_LOCK.lock();
            _purgeExpiredOrphanedTransactions();
        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public void deletePendingTransaction(final PendingTransactionId pendingTransactionId) throws DatabaseException {
        try {
            WRITE_LOCK.lock();
            final MutableList<PendingTransactionId> pendingTransactionIds = new MutableList<PendingTransactionId>(1);
            pendingTransactionIds.add(pendingTransactionId);
            _deletePendingTransactions(pendingTransactionIds);
        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public void deletePendingTransactions(final List<PendingTransactionId> pendingTransactionIds) throws DatabaseException {
        try {
            WRITE_LOCK.lock();
            _deletePendingTransactions(pendingTransactionIds);
        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public void updateTransactionDependencies(final Transaction transaction) throws DatabaseException {
        try {
            WRITE_LOCK.lock();
            _updateTransactionDependencies(transaction);
        }
        finally {
            WRITE_LOCK.unlock();
        }
    }
}
