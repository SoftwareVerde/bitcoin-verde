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
            final Long aStart = System.nanoTime();
            final TransactionId uncommittedPreviousOutputTransactionId = transactionDatabaseManager.getUncommittedTransactionIdFromHash(previousOutputTransactionHash);
            final Long aEnd = System.nanoTime();
            _aCount += 1;
            _aDuration += (aEnd - aStart);
            if (uncommittedPreviousOutputTransactionId != null) {
                previousOutputTransactionId = uncommittedPreviousOutputTransactionId;
            }
            else {
                final Long bStart = System.nanoTime();
                final TransactionId committedPreviousOutputTransactionId = transactionDatabaseManager.getTransactionIdFromHash(blockChainSegmentId, previousOutputTransactionHash);
                previousOutputTransactionId = committedPreviousOutputTransactionId;
                final Long bEnd = System.nanoTime();
                _bCount += 1;
                _bDuration += (bEnd - bStart);
            }
        }
        if (previousOutputTransactionId == null) { return null; }

        final Long cStart = System.nanoTime();
        final TransactionOutputId transactionOutputId = transactionOutputDatabaseManager.findTransactionOutput(previousOutputTransactionId, transactionInput.getPreviousOutputIndex());
        final Long cEnd = System.nanoTime();
        _cCount += 1;
        _cDuration += (cEnd - cStart);
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

    public static long _aCount = 0L;
    public static long _aDuration = 0L;

    public static long _bCount = 0L;
    public static long _bDuration = 0L;

    public static long _cCount = 0L;
    public static long _cDuration = 0L;

    protected TransactionInputId _insertTransactionInput(final BlockChainSegmentId blockChainSegmentId, final TransactionId transactionId, final TransactionInput transactionInput) throws DatabaseException {

        final TransactionOutputId previousTransactionOutputId = _findPreviousTransactionOutputId(blockChainSegmentId, transactionInput);
        final ByteArray unlockingScript = transactionInput.getUnlockingScript().getBytes();

        final Long transactionInputId = _databaseConnection.executeSql(
            new Query("INSERT INTO transaction_inputs (transaction_id, previous_transaction_output_id, unlocking_script, sequence_number) VALUES (?, ?, ?, ?)")
                .setParameter(transactionId)
                .setParameter(previousTransactionOutputId)
                .setParameter(unlockingScript.getBytes())
                .setParameter(transactionInput.getSequenceNumber())
        );

        return TransactionInputId.wrap(transactionInputId);
    }

    public TransactionInputDatabaseManager(final MysqlDatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    public TransactionInputId findTransactionInputId(final BlockChainSegmentId blockChainSegmentId, final TransactionId transactionId, final TransactionInput transactionInput) throws DatabaseException {
        final TransactionOutputId previousTransactionOutputId = _findPreviousTransactionOutputId(blockChainSegmentId, transactionInput);
        return _findTransactionInputId(transactionId, previousTransactionOutputId);
    }

    public void updateTransactionInput(final TransactionInputId transactionInputId, final BlockChainSegmentId blockChainSegmentId, final TransactionId transactionId, final TransactionInput transactionInput) throws DatabaseException {
        _updateTransactionInput(transactionInputId, blockChainSegmentId, transactionId, transactionInput);
    }

    public TransactionInputId insertTransactionInput(final BlockChainSegmentId blockChainSegmentId, final TransactionId transactionId, final TransactionInput transactionInput) throws DatabaseException {
        return _insertTransactionInput(blockChainSegmentId, transactionId, transactionInput);
    }

    public TransactionInput fromDatabaseConnection(final TransactionInputId transactionInputId) throws DatabaseException {
        final ScriptInflater scriptInflater = new ScriptInflater();

        final List<Row> rows = _databaseConnection.query(
            new Query("SELECT * FROM transaction_inputs WHERE id = ?")
                .setParameter(transactionInputId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);

        final MutableTransactionInput transactionInput = new MutableTransactionInput();

        final Sha256Hash previousOutputTransactionHash;
        final Integer previousOutputIndex;
        {
            final List<Row> previousOutputTransactionRows = _databaseConnection.query(
                new Query("SELECT transaction_outputs.id, transactions.hash, transaction_outputs.`index` FROM transaction_outputs INNER JOIN transactions ON (transaction_outputs.transaction_id = transactions.id) WHERE transaction_outputs.id = ?")
                    .setParameter(row.getLong("previous_transaction_output_id"))
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

        final UnlockingScript unlockingScript = UnlockingScript.castFrom(scriptInflater.fromBytes(row.getBytes("unlocking_script")));
        final SequenceNumber sequenceNumber = new ImmutableSequenceNumber(row.getLong("sequence_number"));

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
