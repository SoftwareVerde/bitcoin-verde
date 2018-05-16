package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInputId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;

import java.util.List;

public class TransactionInputDatabaseManager {
    protected final MysqlDatabaseConnection _databaseConnection;

    protected TransactionOutputId _findPreviousTransactionOutputId(final BlockChainSegmentId blockChainSegmentId, final TransactionInput transactionInput) throws DatabaseException {
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(_databaseConnection);
        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(_databaseConnection);

        final Sha256Hash previousOutputTransactionHash = transactionInput.getPreviousOutputTransactionHash();

        final TransactionId previousOutputTransactionId;
        {
            final TransactionId uncommittedPreviousOutputTransactionId = transactionDatabaseManager.getUncommittedTransactionIdFromHash(previousOutputTransactionHash);
            if (uncommittedPreviousOutputTransactionId != null) {
                previousOutputTransactionId = uncommittedPreviousOutputTransactionId;
            }
            else {
                final TransactionId committedPreviousOutputTransactionId = transactionDatabaseManager.getTransactionIdFromHash(blockChainSegmentId, previousOutputTransactionHash);
                previousOutputTransactionId = committedPreviousOutputTransactionId;
            }
        }
        if (previousOutputTransactionId == null) { return null; }

        return transactionOutputDatabaseManager.findTransactionOutput(previousOutputTransactionId, transactionInput.getPreviousOutputIndex());
    }

    protected TransactionInputId _findTransactionInputId(final TransactionId transactionId, final TransactionOutputId previousTransactionOutputId) throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM transaction_inputs WHERE transaction_id = ? AND previous_transaction_output_id = ?")
            .setParameter(transactionId)
            .setParameter(previousTransactionOutputId)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionInputId.wrap(row.getLong("id"));
    }

    protected void _updateTransactionInput(final TransactionInputId transactionInputId, final BlockChainSegmentId blockChainSegmentId, final TransactionId transactionId, final TransactionInput transactionInput) throws DatabaseException {
        final TransactionOutputId previousTransactionOutputId = _findPreviousTransactionOutputId(blockChainSegmentId, transactionInput);
        final ByteArray unlockingScript = transactionInput.getUnlockingScript().getBytes();

        _databaseConnection.executeSql(
            new Query("UPDATE transaction_inputs SET transaction_id = ?, previous_transaction_output_id = ?, unlocking_script = ?, sequence_number = ? WHERE id = ?")
                .setParameter(transactionId)
                .setParameter(previousTransactionOutputId)
                .setParameter(unlockingScript.getBytes())
                .setParameter(transactionInput.getSequenceNumber())
                .setParameter(transactionInputId)
        );
    }

    protected TransactionInputId _insertTransactionInput(final BlockChainSegmentId blockChainSegmentId, final TransactionId transactionId, final TransactionInput transactionInput) throws DatabaseException {
        final TransactionOutputId previousTransactionOutputId = _findPreviousTransactionOutputId(blockChainSegmentId, transactionInput);
        final ByteArray unlockingScript = transactionInput.getUnlockingScript().getBytes();

        return TransactionInputId.wrap(_databaseConnection.executeSql(
            new Query("INSERT INTO transaction_inputs (transaction_id, previous_transaction_output_id, unlocking_script, sequence_number) VALUES (?, ?, ?, ?)")
                .setParameter(transactionId)
                .setParameter(previousTransactionOutputId)
                .setParameter(unlockingScript.getBytes())
                .setParameter(transactionInput.getSequenceNumber())
        ));
    }

    public TransactionInputDatabaseManager(final MysqlDatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    public TransactionInputId storeTransactionInput(final BlockChainSegmentId blockChainSegmentId, final TransactionId transactionId, final TransactionInput transactionInput) throws DatabaseException {
        final TransactionOutputId previousTransactionOutputId = _findPreviousTransactionOutputId(blockChainSegmentId, transactionInput);
        final TransactionInputId transactionInputId = _findTransactionInputId(transactionId, previousTransactionOutputId);

        if (transactionInputId != null) {
            _updateTransactionInput(transactionInputId, blockChainSegmentId, transactionId, transactionInput);
            return transactionInputId;
        }

        return _insertTransactionInput(blockChainSegmentId, transactionId, transactionInput);
    }
}
