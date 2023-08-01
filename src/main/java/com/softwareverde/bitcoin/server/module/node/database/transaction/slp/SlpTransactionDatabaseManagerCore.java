package com.softwareverde.bitcoin.server.module.node.database.transaction.slp;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.properties.PropertiesStore;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.util.Util;

public class SlpTransactionDatabaseManagerCore implements SlpTransactionDatabaseManager {
    protected static final String LAST_SLP_VALIDATED_BLOCK_ID_KEY = "last_slp_validated_block_id";

    protected final FullNodeDatabaseManager _databaseManager;

    protected BlockId _getLastSlpValidatedBlockId() {
        final PropertiesStore propertiesStore = _databaseManager.getPropertiesStore();
        return BlockId.wrap(Util.coalesce(propertiesStore.getLong(LAST_SLP_VALIDATED_BLOCK_ID_KEY)));
    }

    protected List<TransactionId> _getConfirmedPendingValidationSlpTransactions(final BlockId blockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query(
                "SELECT indexed_transaction_outputs.transaction_id FROM indexed_transaction_outputs INNER JOIN block_transactions ON (block_transactions.transaction_id = indexed_transaction_outputs.transaction_id) WHERE block_transactions.block_id = ? AND NOT EXISTS (SELECT * FROM validated_slp_transactions WHERE validated_slp_transactions.transaction_id = indexed_transaction_outputs.transaction_id) AND indexed_transaction_outputs.slp_transaction_id IS NOT NULL"
            )
            .setParameter(blockId)
        );

        ImmutableListBuilder<TransactionId> transactionIds = new ImmutableListBuilder<>();

        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
            if (transactionId == null) { continue; }

            transactionIds.add(transactionId);
        }

        return transactionIds.build();
    }

    protected List<TransactionId> _getUnconfirmedPendingValidationSlpTransactions(final Integer maxCount) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query(
                "SELECT " +
                    "indexed_transaction_outputs.transaction_id " +
                "FROM " +
                    "indexed_transaction_outputs " +
                    "INNER JOIN unconfirmed_transactions " +
                        "ON (unconfirmed_transactions.transaction_id = indexed_transaction_outputs.transaction_id) " +
                    "LEFT OUTER JOIN validated_slp_transactions " +
                        "ON (validated_slp_transactions.transaction_id = indexed_transaction_outputs.transaction_id) " +
                "WHERE " +
                    "validated_slp_transactions.id IS NULL " +
                    "AND indexed_transaction_outputs.slp_transaction_id IS NOT NULL " +
                "GROUP BY indexed_transaction_outputs.transaction_id ASC " +
                "LIMIT " + maxCount
            )
        );

        final ImmutableListBuilder<TransactionId> transactionIds = new ImmutableListBuilder<>(rows.size());

        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
            if (transactionId == null) { continue; }

            transactionIds.add(transactionId);
        }

        return transactionIds.build();
    }

    public SlpTransactionDatabaseManagerCore(final FullNodeDatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    /**
     * Returns the cached SLP validity of the TransactionId.
     *  This function does not run validation on the transaction and only queries its cached value.
     */
    @Override
    public Boolean getSlpTransactionValidationResult(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, is_valid FROM validated_slp_transactions WHERE transaction_id = ?")
                .setParameter(transactionId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return row.getBoolean("is_valid");
    }

    @Override
    public void setSlpTransactionValidationResult(final TransactionId transactionId, final Boolean isValid) throws DatabaseException {
        if (transactionId == null) { return; }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Integer isValidIntegerValue = ( (isValid != null) ? (isValid ? 1 : 0) : null );

        databaseConnection.executeSql(
            new Query("INSERT INTO validated_slp_transactions (transaction_id, is_valid) VALUES (?, ?) ON DUPLICATE KEY UPDATE is_valid = ?")
                .setParameter(transactionId)
                .setParameter(isValidIntegerValue)
                .setParameter(isValidIntegerValue)
        );
    }

    /**
     * Returns the TransactionIds of SLP transactions in the given block that have not been validated yet.
     *  Unconfirmed transactions are not returned by this function.
     */
    @Override
    public List<TransactionId> getConfirmedPendingValidationSlpTransactions(final BlockId blockId) throws DatabaseException {
        return _getConfirmedPendingValidationSlpTransactions(blockId);
    }

    @Override
    public BlockId getLastSlpValidatedBlockId() throws DatabaseException {
        return _getLastSlpValidatedBlockId();
    }

    @Override
    public void setLastSlpValidatedBlockId(final BlockId blockId) {
        final PropertiesStore propertiesStore = _databaseManager.getPropertiesStore();
        propertiesStore.set(LAST_SLP_VALIDATED_BLOCK_ID_KEY, blockId);
    }

    /**
     * Returns a list of (SLP) TransactionIds that have not been validated yet that reside in the mempool.
     */
    @Override
    public List<TransactionId> getUnconfirmedPendingValidationSlpTransactions(final Integer maxCount) throws DatabaseException {
        return _getUnconfirmedPendingValidationSlpTransactions(maxCount);
    }

    @Override
    public void deleteAllSlpValidationResults() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        _databaseManager.startTransaction();

        databaseConnection.executeSql(
                new Query("DELETE FROM validated_slp_transactions")
        );

        databaseConnection.executeSql(
                new Query("DELETE FROM properties WHERE `key` = ?")
                        .setParameter(LAST_SLP_VALIDATED_BLOCK_ID_KEY)
        );

        _databaseManager.commitTransaction();
    }
}
