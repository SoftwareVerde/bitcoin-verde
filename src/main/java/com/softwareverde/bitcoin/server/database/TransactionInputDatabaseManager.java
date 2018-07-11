package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInputId;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableSequenceNumber;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.script.ScriptInflater;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.unlocking.MutableUnlockingScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.MutableSha256Hash;
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

        final TransactionOutputId transactionOutputId = transactionOutputDatabaseManager.findTransactionOutput(previousOutputTransactionId, transactionInput.getPreviousOutputIndex());
        return transactionOutputId;
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

    protected TransactionInputId _insertTransactionInput(final BlockChainSegmentId blockChainSegmentId, final TransactionId transactionId, final TransactionInput transactionInput) throws DatabaseException {

        final TransactionOutputId previousTransactionOutputId = _findPreviousTransactionOutputId(blockChainSegmentId, transactionInput);
        final UnlockingScript unlockingScript = transactionInput.getUnlockingScript();

        final Long transactionInputIdLong = _databaseConnection.executeSql(
            new Query("INSERT INTO transaction_inputs (transaction_id, previous_transaction_output_id, sequence_number) VALUES (?, ?, ?)")
                .setParameter(transactionId)
                .setParameter(previousTransactionOutputId)
                .setParameter(transactionInput.getSequenceNumber())
        );

        final TransactionInputId transactionInputId = TransactionInputId.wrap(transactionInputIdLong);
        if (transactionInputId == null) { return null; }

        _insertUnlockingScript(transactionInputId, unlockingScript);

        return transactionInputId;
    }

    public void _insertUnlockingScript(final TransactionInputId transactionInputId, final UnlockingScript unlockingScript) throws DatabaseException {
        _databaseConnection.executeSql(
            new Query("INSERT INTO unlocking_scripts (transaction_input_id, script) VALUES (?, ?)")
                .setParameter(transactionInputId)
                .setParameter(unlockingScript.getBytes().getBytes())
        );
    }

    public TransactionInputDatabaseManager(final MysqlDatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    public TransactionInputId findTransactionInputId(final BlockChainSegmentId blockChainSegmentId, final TransactionId transactionId, final TransactionInput transactionInput) throws DatabaseException {
        final TransactionOutputId previousTransactionOutputId = _findPreviousTransactionOutputId(blockChainSegmentId, transactionInput);
        return _findTransactionInputId(transactionId, previousTransactionOutputId);
    }

    public TransactionInputId insertTransactionInput(final BlockChainSegmentId blockChainSegmentId, final TransactionId transactionId, final TransactionInput transactionInput) throws DatabaseException {
        return _insertTransactionInput(blockChainSegmentId, transactionId, transactionInput);
    }

    public TransactionInput getTransactionInput(final TransactionInputId transactionInputId) throws DatabaseException {
        final List<Row> transactionInputRows = _databaseConnection.query(
            new Query("SELECT * FROM transaction_inputs WHERE id = ?")
                .setParameter(transactionInputId)
        );
        if (transactionInputRows.isEmpty()) { return null; }

        final Row transactionInputRow = transactionInputRows.get(0);

        final MutableTransactionInput transactionInput = new MutableTransactionInput();

        final Sha256Hash previousOutputTransactionHash;
        final Integer previousOutputIndex;
        {
            final List<Row> previousOutputTransactionRows = _databaseConnection.query(
                new Query("SELECT transaction_outputs.id, transactions.hash, transaction_outputs.`index` FROM transaction_outputs INNER JOIN transactions ON (transaction_outputs.transaction_id = transactions.id) WHERE transaction_outputs.id = ?")
                    .setParameter(transactionInputRow.getLong("previous_transaction_output_id"))
            );
            if (previousOutputTransactionRows.isEmpty()) {
                previousOutputTransactionHash = new ImmutableSha256Hash();
                previousOutputIndex = -1;
            }
            else {
                final Row previousOutputTransactionRow = previousOutputTransactionRows.get(0);
                previousOutputTransactionHash = MutableSha256Hash.fromHexString(previousOutputTransactionRow.getString("hash"));
                previousOutputIndex = previousOutputTransactionRow.getInteger("index");
            }
        }

        final UnlockingScript unlockingScript;
        {
            final List<Row> rows = _databaseConnection.query(
                new Query("SELECT id, script FROM unlocking_scripts WHERE transaction_input_id = ?")
                    .setParameter(transactionInputId)
            );
            if (rows.isEmpty()) { return null; }

            final Row row = rows.get(0);
            unlockingScript = new MutableUnlockingScript(row.getBytes("script"));
        }

        final SequenceNumber sequenceNumber = new ImmutableSequenceNumber(transactionInputRow.getLong("sequence_number"));

        transactionInput.setPreviousOutputTransactionHash(previousOutputTransactionHash);
        transactionInput.setPreviousOutputIndex(previousOutputIndex);

        transactionInput.setUnlockingScript(unlockingScript);
        transactionInput.setSequenceNumber(sequenceNumber);

        return transactionInput;
    }

    public TransactionOutputId findPreviousTransactionOutputId(final BlockChainSegmentId blockChainSegmentId, final TransactionInput transactionInput) throws DatabaseException {
        return _findPreviousTransactionOutputId(blockChainSegmentId, transactionInput);
    }
}
