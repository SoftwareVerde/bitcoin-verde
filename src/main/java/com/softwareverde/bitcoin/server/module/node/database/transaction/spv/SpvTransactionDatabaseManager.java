package com.softwareverde.bitcoin.server.module.node.database.transaction.spv;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.type.time.SystemTime;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SpvTransactionDatabaseManager implements TransactionDatabaseManager {
    protected static final ReentrantReadWriteLock.ReadLock READ_LOCK;
    protected static final ReentrantReadWriteLock.WriteLock WRITE_LOCK;
    static {
        final ReentrantReadWriteLock reentrantReadWriteLock = new ReentrantReadWriteLock();
        READ_LOCK = reentrantReadWriteLock.readLock();
        WRITE_LOCK = reentrantReadWriteLock.writeLock();
    }

    protected static final SystemTime _systemTime = new SystemTime();
    protected final DatabaseManager _databaseManager;

    protected TransactionId _getTransactionIdFromHash(final Sha256Hash transactionHash) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM transactions WHERE hash = ?")
                .setParameter(transactionHash)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionId.wrap(row.getLong("id"));
    }

    protected List<BlockId> _getBlockIds(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, block_id FROM block_transactions WHERE transaction_id = ?")
                .setParameter(transactionId)
        );

        final MutableList<BlockId> blockIds = new MutableList<BlockId>(rows.size());
        for (final Row row : rows) {
            final Long blockId = row.getLong("block_id");
            blockIds.add(BlockId.wrap(blockId));
        }
        return blockIds;
    }

    protected TransactionId _insertTransaction(final Transaction transaction) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Sha256Hash transactionHash = transaction.getHash();

        final Long transactionIdLong = databaseConnection.executeSql(
            new Query("INSERT INTO transactions (hash) VALUES (?)")
                .setParameter(transactionHash)
        );

        final TransactionId transactionId = TransactionId.wrap(transactionIdLong);
        if (transactionId == null) { return null; }

        final TransactionDeflater transactionDeflater = new TransactionDeflater();
        final ByteArray transactionBytes = transactionDeflater.toBytes(transaction);

        databaseConnection.executeSql(
            new Query("INSERT INTO transaction_data (transaction_id, data) VALUES (?, ?)")
                .setParameter(transactionId)
                .setParameter(transactionBytes.getBytes())
        );

        return transactionId;
    }

    protected TransactionId _storeTransaction(final Transaction transaction) throws DatabaseException {
        final Sha256Hash transactionHash = transaction.getHash();

        final TransactionId existingTransactionId = _getTransactionIdFromHash(transactionHash);
        if (existingTransactionId != null) {
            return existingTransactionId;
        }

        return _insertTransaction(transaction);
    }

    /**
     * Returns a map of newly inserted Transactions and their Ids.  If a transaction already existed, its Hash/Id pair are not returned within the map.
     */
    protected Map<Sha256Hash, TransactionId> _storeTransactions(final List<Transaction> transactions) throws DatabaseException {
        final HashMap<Sha256Hash, TransactionId> transactionHashMap = new HashMap<Sha256Hash, TransactionId>(transactions.getSize());

        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();

            final TransactionId transactionId;
            {
                final TransactionId existingTransactionId = _getTransactionIdFromHash(transactionHash);
                if (existingTransactionId != null) {
                    transactionId = existingTransactionId;
                }
                else {
                    transactionId = _insertTransaction(transaction);
                }
            }

            transactionHashMap.put(transactionHash, transactionId);
        }

        return transactionHashMap;
    }

    protected Transaction _inflateTransaction(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT transaction_id, data FROM transaction_data WHERE transaction_id = ?")
                .setParameter(transactionId)
        );
        if (rows.isEmpty()) { return null; }

        final TransactionInflater transactionInflater = new TransactionInflater();
        final Row row = rows.get(0);

        final ByteArray transactionBytes = MutableByteArray.wrap(row.getBytes("data"));
        return transactionInflater.fromBytes(transactionBytes);
    }

    public SpvTransactionDatabaseManager(final DatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    @Override
    public TransactionId storeTransaction(final Transaction transaction) throws DatabaseException {
        WRITE_LOCK.lock();
        try {
            return _storeTransaction(transaction);
        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public TransactionId storeTransactionHash(final Sha256Hash transactionHash) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        WRITE_LOCK.lock();
        try {
            final Long transactionIdLong = databaseConnection.executeSql(
                new Query("INSERT INTO transactions (hash) VALUES (?)")
                    .setParameter(transactionHash)
            );
            return TransactionId.wrap(transactionIdLong);
        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public List<TransactionId> getTransactionIds() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        READ_LOCK.lock();
        try {
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT id FROM transactions")
            );

            final ImmutableListBuilder<TransactionId> transactionIds = new ImmutableListBuilder<TransactionId>(rows.size());
            for (final Row row : rows) {
                final Long transactionIdLong = row.getLong("id");
                transactionIds.add(TransactionId.wrap(transactionIdLong));
            }
            return transactionIds.build();
        }
        finally {
            READ_LOCK.unlock();
        }
    }

    @Override
    public List<TransactionId> storeTransactions(final List<Transaction> transactions) throws DatabaseException {
        WRITE_LOCK.lock();
        try {
            final MutableList<TransactionId> transactionIds = new MutableList<TransactionId>(transactions.getSize());
            for (final Transaction transaction : transactions) {
                transactionIds.add(_storeTransaction(transaction));
            }

            return transactionIds;
        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    @Override
    public TransactionId getTransactionId(final Sha256Hash transactionHash) throws DatabaseException {
        READ_LOCK.lock();
        try {
            return _getTransactionIdFromHash(transactionHash);
        }
        finally {
            READ_LOCK.unlock();
        }
    }

    @Override
    public Sha256Hash getTransactionHash(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        READ_LOCK.lock();
        try {
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT id, hash FROM transactions WHERE id = ?")
                    .setParameter(transactionId)
            );
            if (rows.isEmpty()) { return null; }

            final Row row = rows.get(0);
            return Sha256Hash.fromHexString(row.getString("hash"));
        }
        finally {
            READ_LOCK.unlock();
        }
    }

    @Override
    public Transaction getTransaction(final TransactionId transactionId) throws DatabaseException {
        READ_LOCK.lock();
        try {
            return _inflateTransaction(transactionId);
        }
        finally {
            READ_LOCK.unlock();
        }
    }

    @Override
    public BlockId getBlockId(final BlockchainSegmentId blockchainSegmentId, final TransactionId transactionId) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

        READ_LOCK.lock();
        try {
            final List<BlockId> blockIds = _getBlockIds(transactionId);
            for (final BlockId blockId : blockIds) {
                final Boolean isConnected = blockHeaderDatabaseManager.isBlockConnectedToChain(blockId, blockchainSegmentId, BlockRelationship.ANY);
                if (isConnected) {
                    return blockId;
                }
            }

            return null;
        }
        finally {
            READ_LOCK.unlock();
        }
    }

    @Override
    public List<BlockId> getBlockIds(final TransactionId transactionId) throws DatabaseException {
        READ_LOCK.lock();
        try {
            return _getBlockIds(transactionId);
        }
        finally {
            READ_LOCK.unlock();
        }
    }

    @Override
    public List<BlockId> getBlockIds(final Sha256Hash transactionHash) throws DatabaseException {
        READ_LOCK.lock();
        try {
            final TransactionId transactionId = _getTransactionIdFromHash(transactionHash);
            if (transactionId == null) { return new MutableList<BlockId>(); }

            return _getBlockIds(transactionId);
        }
        finally {
            READ_LOCK.unlock();
        }
    }

    public SlpValidity getSlpValidity(final TransactionId transactionId) throws DatabaseException {
        READ_LOCK.lock();
        try {
            final Query query = new Query("SELECT slp_validity FROM transactions WHERE id = ?");
            query.setParameter(transactionId);

            final java.util.List<Row> rows = _databaseManager.getDatabaseConnection().query(query);
            if (rows.size() == 0) {
                return null;
            }

            final String slpValidity = rows.get(0).getString("slp_validity");
            return SlpValidity.valueOf(slpValidity);
        }
        finally {
            READ_LOCK.unlock();
        }
    }

    public void setSlpValidity(final TransactionId transactionId, final SlpValidity validity) throws DatabaseException {
        WRITE_LOCK.lock();
        try {
            final Query query = new Query("UPDATE transactions SET slp_validity = ? WHERE id = ?");
            query.setParameter(validity.toString());
            query.setParameter(transactionId);

            _databaseManager.getDatabaseConnection().executeSql(query);
        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public List<Sha256Hash> getSlpTransactionsWithUnknownValidity() throws DatabaseException {
        READ_LOCK.lock();
        try {
            final Query query = new Query("SELECT hash FROM transactions WHERE slp_validity = 'UNKNOWN'");

            final java.util.List<Row> rows = _databaseManager.getDatabaseConnection().query(query);

            final MutableList<Sha256Hash> hashes = new MutableList<>();
            for (final Row row : rows) {
                final String hashString = row.getString("hash");
                final Sha256Hash hash = MutableSha256Hash.wrap(HexUtil.hexStringToByteArray(hashString));
                hashes.add(hash);
            }
            return hashes;
        }
        finally {
            READ_LOCK.unlock();
        }
    }
}
