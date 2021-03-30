package com.softwareverde.bitcoin.server.module.node.handler.transaction.dsproof;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProof;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProofInflater;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;

public class DoubleSpendProofDatabase extends DoubleSpendProofStore {
    protected final DatabaseManagerFactory _databaseManagerFactory;

    public DoubleSpendProofDatabase(final Integer maxCachedItemCount, final DatabaseManagerFactory databaseConnectionFactory) {
        super(maxCachedItemCount);
        _databaseManagerFactory = databaseConnectionFactory;
    }

    @Override
    public Boolean storeDoubleSpendProof(final DoubleSpendProof doubleSpendProof) {
        final Boolean superValue = super.storeDoubleSpendProof(doubleSpendProof);

        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
            final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            final Sha256Hash doubleSpendProofHash = doubleSpendProof.getHash();
            final TransactionOutputIdentifier transactionOutputIdentifier = doubleSpendProof.getTransactionOutputIdentifierBeingDoubleSpent();
            final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
            final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();
            final ByteArray doubleSpendProofBytes = doubleSpendProof.getBytes();

            final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
            if (transactionId != null) {
                databaseConnection.executeSql(
                    new Query("INSERT INTO double_spend_proofs (hash, transaction_id, output_index, data) VALUES (?, ?, ?, ?)")
                        .setParameter(doubleSpendProofHash)
                        .setParameter(transactionId)
                        .setParameter(outputIndex)
                        .setParameter(doubleSpendProofBytes)
                );
            }
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
        }

        return superValue;
    }

    @Override
    public DoubleSpendProof getDoubleSpendProof(final Sha256Hash doubleSpendProofHash) {
        final DoubleSpendProof superValue = super.getDoubleSpendProof(doubleSpendProofHash);
        if (superValue != null) { return superValue; }

        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final DoubleSpendProofInflater doubleSpendProofInflater = new DoubleSpendProofInflater();
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT hash, data FROM double_spend_proofs WHERE hash = ?")
                    .setParameter(doubleSpendProofHash)
            );
            if (rows.isEmpty()) { return null; }

            final Row row = rows.get(0);
            final ByteArray byteArray = ByteArray.wrap(row.getBytes("data"));

            return doubleSpendProofInflater.fromBytes(byteArray);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public DoubleSpendProof getDoubleSpendProof(final TransactionOutputIdentifier transactionOutputIdentifier) {
        final DoubleSpendProof superValue = super.getDoubleSpendProof(transactionOutputIdentifier);
        if (superValue != null) { return superValue; }

        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
            final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
            final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

            final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
            if (transactionId == null) { return null; }

            final DoubleSpendProofInflater doubleSpendProofInflater = new DoubleSpendProofInflater();
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT hash, data FROM double_spend_proofs WHERE transaction_id = ? AND output_index = ?")
                    .setParameter(transactionId)
                    .setParameter(outputIndex)
            );
            if (rows.isEmpty()) { return null; }

            final Row row = rows.get(0);
            final ByteArray byteArray = ByteArray.wrap(row.getBytes("data"));

            return doubleSpendProofInflater.fromBytes(byteArray);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }
}
