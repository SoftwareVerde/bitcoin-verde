package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.BlockCache;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.query.ValueExtractor;
import com.softwareverde.database.query.parameter.InClauseParameter;
import com.softwareverde.database.query.parameter.TypedParameter;
import com.softwareverde.database.row.Row;

public class NonIndexingFullNodeTransactionDatabaseManagerCore implements FullNodeTransactionDatabaseManager {
    protected final FullNodeDatabaseManager _databaseManager;
    protected final MasterInflater _masterInflater;
    protected final BlockCache _blockCache;

    public NonIndexingFullNodeTransactionDatabaseManagerCore(final FullNodeDatabaseManager databaseManager, final BlockCache blockCache, final MasterInflater masterInflater) {
        _databaseManager = databaseManager;
        _masterInflater = masterInflater;
        _blockCache = blockCache;
    }

    @Override
    public Transaction getTransaction(final TransactionId transactionId, final Boolean shouldUpdateUnspentOutputCache) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        { // Attempt to load the Transaction from a Block on disk...
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT blocks.hash AS block_hash, blocks.block_height, block_transactions.disk_offset, transactions.byte_count FROM transactions INNER JOIN block_transactions ON transactions.id = block_transactions.transaction_id INNER JOIN blocks ON blocks.id = block_transactions.block_id WHERE transactions.id = ? LIMIT 1")
                    .setParameter(transactionId)
            );
            if (! rows.isEmpty()) {
                final Row row = rows.get(0);
                final Sha256Hash blockHash = Sha256Hash.fromHexString(row.getString("block_hash"));
                final Long blockHeight = row.getLong("block_height");
                final Long diskOffset = row.getLong("disk_offset");
                final Integer byteCount = row.getInteger("byte_count");

                final ByteArray transactionData = _blockCache.readFromBlock(blockHash, blockHeight, diskOffset, byteCount);
                if (transactionData == null) { return null; }

                final TransactionInflater transactionInflater = _masterInflater.getTransactionInflater();
                return transactionInflater.fromBytes(transactionData);
            }
        }

        { // Attempt to load the Transaction from the Unconfirmed Transactions table...
            // TODO
        }

        return null;
    }

    @Override
    public Boolean previousOutputsExist(final Transaction transaction) throws DatabaseException {
        return null;
    }

    @Override
    public void addToUnconfirmedTransactions(final TransactionId transactionId) throws DatabaseException {

    }

    @Override
    public void addToUnconfirmedTransactions(final List<TransactionId> transactionIds) throws DatabaseException {

    }

    @Override
    public void removeFromUnconfirmedTransactions(final TransactionId transactionId) throws DatabaseException {

    }

    @Override
    public void removeFromUnconfirmedTransactions(final List<TransactionId> transactionIds) throws DatabaseException {

    }

    @Override
    public Boolean isUnconfirmedTransaction(final TransactionId transactionId) throws DatabaseException {
        return null;
    }

    @Override
    public List<TransactionId> getUnconfirmedTransactionIds() throws DatabaseException {
        return null;
    }

    @Override
    public List<TransactionId> getUnconfirmedTransactionsDependingOnSpentInputsOf(final List<TransactionId> transactionIds) throws DatabaseException {
        return null;
    }

    @Override
    public List<TransactionId> getUnconfirmedTransactionsDependingOn(final List<TransactionId> transactionIds) throws DatabaseException {
        return null;
    }

    @Override
    public Integer getUnconfirmedTransactionCount() throws DatabaseException {
        return null;
    }

    @Override
    public Long calculateTransactionFee(final Transaction transaction) throws DatabaseException {
        return null;
    }

    @Override
    public void updateTransaction(final Transaction transaction) throws DatabaseException {

    }

    @Override
    public void deleteTransaction(final TransactionId transactionId) throws DatabaseException {

    }

    @Override
    public SlpTokenId getSlpTokenId(final Sha256Hash transactionHash) throws DatabaseException {
        return null;
    }

    @Override
    public TransactionId storeTransaction(final Transaction transaction) throws DatabaseException {
        final Sha256Hash transactionHash = transaction.getHash();

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM transactions WHERE hash = ?")
                .setParameter(transactionHash)
        );
        if (! rows.isEmpty()) {
            final Row row = rows.get(0);
            final Long transactionId = row.getLong("id");
            return TransactionId.wrap(transactionId);
        }

        final TransactionDeflater transactionDeflater = _masterInflater.getTransactionDeflater();
        final Integer transactionByteCount = transactionDeflater.getByteCount(transaction);

        final Long transactionId = databaseConnection.executeSql(
            new Query("INSERT INTO transactions (hash, byte_count) VALUES (?, ?)")
                .setParameter(transactionHash)
                .setParameter(transactionByteCount)
        );

        return TransactionId.wrap(transactionId);
    }

    @Override
    public List<TransactionId> storeTransactions(final List<Transaction> transactions) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final TransactionDeflater transactionDeflater = _masterInflater.getTransactionDeflater();

        final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT IGNORE INTO transactions (hash, byte_count) VALUES (?, ?)");

        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();
            final Integer transactionByteCount = transactionDeflater.getByteCount(transaction);

            batchedInsertQuery.setParameter(transactionHash);
            batchedInsertQuery.setParameter(transactionByteCount);
        }

        databaseConnection.executeSql(batchedInsertQuery);

        final Query query = new Query("SELECT id, hash FROM transactions WHERE hash IN (?)");
        query.setInClauseParameters(transactions, new ValueExtractor<Transaction>() {
            @Override
            public InClauseParameter extractValues(final Transaction transaction) {
                final Sha256Hash transactionHash = transaction.getHash();
                return new InClauseParameter(new TypedParameter(transactionHash.toString()));
            }
        });

        final java.util.List<Row> rows = databaseConnection.query(query);
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("id"));
            final Sha256Hash transactionHash = Sha256Hash.fromHexString(row.getString("hash"));
        }


    }

    @Override
    public TransactionId getTransactionId(final Sha256Hash transactionHash) throws DatabaseException {
        return null;
    }

    @Override
    public Sha256Hash getTransactionHash(final TransactionId transactionId) throws DatabaseException {
        return null;
    }

    @Override
    public Transaction getTransaction(final TransactionId transactionId) throws DatabaseException {
        return null;
    }

    @Override
    public BlockId getBlockId(final BlockchainSegmentId blockchainSegmentId, final TransactionId transactionId) throws DatabaseException {
        return null;
    }

    @Override
    public List<BlockId> getBlockIds(final TransactionId transactionId) throws DatabaseException {
        return null;
    }

    @Override
    public List<BlockId> getBlockIds(final Sha256Hash transactionHash) throws DatabaseException {
        return null;
    }
}
