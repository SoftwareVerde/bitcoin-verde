package com.softwareverde.bitcoin.server.module.node.database.transaction.spv;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.database.query.ValueExtractor;
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
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;
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

    protected TransactionId _getTransactionId(final Sha256Hash transactionHash) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM transactions WHERE hash = ?")
                .setParameter(transactionHash)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionId.wrap(row.getLong("id"));
    }

    protected BlockId _getBlockId(final BlockchainSegmentId blockchainSegmentId, final TransactionId transactionId) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

        final List<BlockId> blockIds = _getBlockIds(transactionId);
        for (final BlockId blockId : blockIds) {
            final Boolean isConnected = blockHeaderDatabaseManager.isBlockConnectedToChain(blockId, blockchainSegmentId, BlockRelationship.ANY);
            if (isConnected) {
                return blockId;
            }
        }

        return null;
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

        final TransactionId existingTransactionId = _getTransactionId(transactionHash);
        if (existingTransactionId != null) {
            return existingTransactionId;
        }

        return _insertTransaction(transaction);
    }

    /**
     * Returns a map of newly inserted Transactions and their Ids.  If a transaction already existed, its Hash/Id pair are not returned within the map.
     */
    protected Map<Sha256Hash, TransactionId> _storeTransactions(final List<Transaction> transactions) throws DatabaseException {
        final HashMap<Sha256Hash, TransactionId> transactionHashMap = new HashMap<Sha256Hash, TransactionId>(transactions.getCount());

        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();

            final TransactionId transactionId;
            {
                final TransactionId existingTransactionId = _getTransactionId(transactionHash);
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

    protected Transaction _getTransaction(final TransactionId transactionId) throws DatabaseException {
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

    public TransactionId storeTransaction(final Transaction transaction) throws DatabaseException {
        WRITE_LOCK.lock();
        try {
            final TransactionId transactionId = _storeTransaction(transaction);

            if (Transaction.isSlpTransaction(transaction)) {
                final SlpValidity slpValidity = _getSlpValidity(transactionId);
                if (slpValidity == null) {
                    _setSlpValidity(transactionId, SlpValidity.UNKNOWN);
                }
            }
            return transactionId;
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

    public List<TransactionId> storeTransactions(final List<Transaction> transactions) throws DatabaseException {
        WRITE_LOCK.lock();
        try {
            final MutableList<TransactionId> transactionIds = new MutableList<TransactionId>(transactions.getCount());
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
            return _getTransactionId(transactionHash);
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
            return Sha256Hash.copyOf(row.getBytes("hash"));
        }
        finally {
            READ_LOCK.unlock();
        }
    }

    @Override
    public Map<Sha256Hash, TransactionId> getTransactionIds(final List<Sha256Hash> transactionHashes) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows;

        READ_LOCK.lock();
        try {
            rows = databaseConnection.query(
                new Query("SELECT id, hash FROM transactions WHERE hash IN(?)")
                    .setInClauseParameters(transactionHashes, ValueExtractor.SHA256_HASH)
            );
        }
        finally {
            READ_LOCK.unlock();
        }

        final int transactionCount = transactionHashes.getCount();
        if (rows.size() != transactionCount) { return null; }

        final HashMap<Sha256Hash, TransactionId> transactionHashesMap = new HashMap<Sha256Hash, TransactionId>(transactionCount);
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("id"));
            final Sha256Hash transactionHash = Sha256Hash.copyOf(row.getBytes("hash"));

            transactionHashesMap.put(transactionHash, transactionId);
        }
        return transactionHashesMap;
    }

    @Override
    public Transaction getTransaction(final TransactionId transactionId) throws DatabaseException {
        READ_LOCK.lock();
        try {
            return _getTransaction(transactionId);
        }
        finally {
            READ_LOCK.unlock();
        }
    }

    @Override
    public Map<Sha256Hash, Transaction> getTransactions(final List<Sha256Hash> transactionHashes) throws DatabaseException {
        READ_LOCK.lock();
        try {
            final HashMap<Sha256Hash, Transaction> transactions = new HashMap<>();
            for (final Sha256Hash transactionHash : transactionHashes) {
                final TransactionId transactionId = _getTransactionId(transactionHash);
                final Transaction transaction = _getTransaction(transactionId);
                transactions.put(transactionHash, transaction);
            }
            return transactions;
        }
        finally {
            READ_LOCK.unlock();
        }
    }

    @Override
    public BlockId getBlockId(final BlockchainSegmentId blockchainSegmentId, final TransactionId transactionId) throws DatabaseException {
        READ_LOCK.lock();
        try {
            return _getBlockId(blockchainSegmentId, transactionId);
        }
        finally {
            READ_LOCK.unlock();
        }
    }

    @Override
    public Map<Sha256Hash, BlockId> getBlockIds(final BlockchainSegmentId blockchainSegmentId, final List<Sha256Hash> transactionHashes) throws DatabaseException {
        READ_LOCK.lock();
        try {
            final HashMap<Sha256Hash, BlockId> blockIds = new HashMap<Sha256Hash, BlockId>(transactionHashes.getCount());
            for (final Sha256Hash transactionHash : transactionHashes) {
                final TransactionId transactionId = _getTransactionId(transactionHash);
                final BlockId blockId = _getBlockId(blockchainSegmentId, transactionId);
                blockIds.put(transactionHash, blockId);
            }
            return blockIds;
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
            final TransactionId transactionId = _getTransactionId(transactionHash);
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
            return _getSlpValidity(transactionId);
        }
        finally {
            READ_LOCK.unlock();
        }
    }

    protected SlpValidity _getSlpValidity(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT slp_validity FROM transactions WHERE id = ?")
                .setParameter(transactionId)
        );

        if (rows.isEmpty()) {
            return null;
        }

        final Row row = rows.get(0);
        final String slpValidity = row.getString("slp_validity");
        if (slpValidity == null) {
            return null;
        }

        return SlpValidity.valueOf(slpValidity);
    }

    public void setSlpValidity(final TransactionId transactionId, final SlpValidity slpValidity) throws DatabaseException {
        WRITE_LOCK.lock();
        try {
            _setSlpValidity(transactionId, slpValidity);
        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    protected void _setSlpValidity(final TransactionId transactionId, final SlpValidity slpValidity) throws DatabaseException {
        Logger.debug("Setting SLP validity of transaction " + transactionId + " to " + slpValidity);

        final Query query = new Query("UPDATE transactions SET slp_validity = ? WHERE id = ?");
        if (slpValidity != null) {
            query.setParameter(slpValidity.toString());
        }
        else {
            query.setNullParameter();
        }
        query.setParameter(transactionId);

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        databaseConnection.executeSql(query);
    }

    public void clearSlpValidity() throws DatabaseException {
        WRITE_LOCK.lock();
        try {
            final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
            databaseConnection.executeSql(
                new Query("UPDATE transactions SET slp_validity = ? WHERE slp_validity IS NOT NULL")
                    .setParameter(SlpValidity.UNKNOWN.toString())
            );
        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public List<Sha256Hash> getSlpTransactionsWithSlpStatus(final SlpValidity slpValidity) throws DatabaseException {
        READ_LOCK.lock();
        try {
            final Query query = new Query("SELECT hash FROM transactions WHERE slp_validity = ?");
            if (slpValidity != null) {
                query.setParameter(slpValidity.toString());
            }
            else {
                query.setNullParameter();
            }

            final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
            final java.util.List<Row> rows = databaseConnection.query(query);

            final MutableList<Sha256Hash> hashes = new MutableList<Sha256Hash>(rows.size());
            for (final Row row : rows) {
                final Sha256Hash hash = Sha256Hash.copyOf(row.getBytes("hash"));
                hashes.add(hash);
            }
            return hashes;
        }
        finally {
            READ_LOCK.unlock();
        }
    }
}
