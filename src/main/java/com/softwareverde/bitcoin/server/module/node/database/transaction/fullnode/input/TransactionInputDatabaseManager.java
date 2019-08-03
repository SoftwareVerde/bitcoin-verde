package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.input;

import com.softwareverde.bitcoin.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.output.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInputId;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableSequenceNumber;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.unlocking.MutableUnlockingScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.timer.NanoTimer;

import java.util.Map;

public class TransactionInputDatabaseManager {
    protected FullNodeDatabaseManager _databaseManager;

//    protected TransactionOutputId _getPreviousTransactionOutputId(final TransactionInput transactionInput) throws DatabaseException {
//        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(databaseConnection, databaseManagerCache);
//        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, databaseManagerCache);
//
//        final Sha256Hash previousOutputTransactionHash = transactionInput.getPreviousOutputTransactionHash();
//
//        final TransactionId previousOutputTransactionId;
//        {
//            final TransactionId cachedTransactionId = databaseManagerCache.getCachedTransactionId(previousOutputTransactionHash.asConst());
//            if (cachedTransactionId != null) {
//                previousOutputTransactionId = cachedTransactionId;
//            }
//            else {
//                previousOutputTransactionId = transactionDatabaseManager.getTransactionId(previousOutputTransactionHash);
//                if (previousOutputTransactionId == null) { return null; }
//            }
//        }
//
//        final TransactionOutputId transactionOutputId = transactionOutputDatabaseManager.findTransactionOutput(previousOutputTransactionId, previousOutputTransactionHash, transactionInput.getPreviousOutputIndex());
//        return transactionOutputId;
//    }

    protected TransactionInputId _findTransactionInputId(final TransactionId transactionId, final TransactionOutputId previousTransactionOutputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM transaction_inputs WHERE transaction_id = ? AND previous_transaction_output_id = ?")
                .setParameter(transactionId)
                .setParameter(previousTransactionOutputId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionInputId.wrap(row.getLong("id"));
    }

    protected TransactionInputId _insertTransactionInput(final TransactionId transactionId, final TransactionInput transactionInput) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getTransactionOutputDatabaseManager();

        final TransactionOutputId previousTransactionOutputId;
        {
            if (Util.areEqual(Sha256Hash.EMPTY_HASH, transactionInput.getPreviousOutputTransactionHash())) {
                // if (! Util.areEqual(-1, transactionInput.getPreviousOutputIndex())) { return null; } // NOTE: This isn't actually enforced in any of the other reference clients...
                previousTransactionOutputId = null;
            }
            else {
                previousTransactionOutputId = transactionOutputDatabaseManager.findTransactionOutput(TransactionOutputIdentifier.fromTransactionInput(transactionInput));
                if (previousTransactionOutputId == null) {
                    throw new DatabaseException("Could not find TransactionInput.previousOutputTransaction: " + transactionId + " " + transactionInput.getPreviousOutputIndex() + ":" + transactionInput.getPreviousOutputTransactionHash());
                }
            }
        }

        final Long transactionInputIdLong = databaseConnection.executeSql(
            new Query("INSERT INTO transaction_inputs (transaction_id, previous_transaction_output_id, sequence_number) VALUES (?, ?, ?)")
                .setParameter(transactionId)
                .setParameter(previousTransactionOutputId)
                .setParameter(transactionInput.getSequenceNumber())
        );
        final TransactionInputId transactionInputId = TransactionInputId.wrap(transactionInputIdLong);

        if (previousTransactionOutputId != null) {
            final TransactionOutputIdentifier previousOutputTransactionOutputIdentifier = new TransactionOutputIdentifier(transactionInput.getPreviousOutputTransactionHash(), transactionInput.getPreviousOutputIndex());
            transactionOutputDatabaseManager.markTransactionOutputAsSpent(previousTransactionOutputId, previousOutputTransactionOutputIdentifier);
        }

        final UnlockingScript unlockingScript = transactionInput.getUnlockingScript();
        _insertUnlockingScript(transactionInputId, unlockingScript);

        return transactionInputId;
    }

    protected void _insertUnlockingScript(final TransactionInputId transactionInputId, final UnlockingScript unlockingScript) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final ByteArray unlockingScriptByteArray = unlockingScript.getBytes();
        databaseConnection.executeSql(
            new Query("INSERT INTO unlocking_scripts (transaction_input_id, script) VALUES (?, ?)")
                .setParameter(transactionInputId)
                .setParameter(unlockingScriptByteArray.getBytes())
        );
    }

    protected void _updateUnlockingScript(final TransactionInputId transactionInputId, final UnlockingScript unlockingScript) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final ByteArray unlockingScriptByteArray = unlockingScript.getBytes();
        databaseConnection.executeSql(
            new Query("UPDATE unlocking_scripts SET script = ? WHERE transaction_input_id = ?")
                .setParameter(unlockingScriptByteArray.getBytes())
                .setParameter(transactionInputId)
        );
    }

    protected void _insertUnlockingScripts(final List<TransactionInputId> transactionInputIds, final List<UnlockingScript> unlockingScripts) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        if (! Util.areEqual(transactionInputIds.getSize(), unlockingScripts.getSize())) {
            throw new DatabaseException("TransactionInputDatabaseManager::_insertUnlockingScripts -- transactionInputIds.getSize must equal unlockingScripts.getSize");
        }

        final Query batchedInsertQuery = new BatchedInsertQuery("INSERT INTO unlocking_scripts (transaction_input_id, script) VALUES (?, ?)");
        for (int i = 0; i < transactionInputIds.getSize(); ++i) {
            final TransactionInputId transactionInputId = transactionInputIds.get(i);
            final UnlockingScript unlockingScript = unlockingScripts.get(i);
            final ByteArray unlockingScriptByteArray = unlockingScript.getBytes();
            batchedInsertQuery.setParameter(transactionInputId);
            batchedInsertQuery.setParameter(unlockingScriptByteArray.getBytes());
        }

        databaseConnection.executeSql(batchedInsertQuery);
    }

    public TransactionInputDatabaseManager(final FullNodeDatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    public TransactionInputId getTransactionInputId(final TransactionId transactionId, final TransactionInput transactionInput) throws DatabaseException {
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getTransactionOutputDatabaseManager();

        final TransactionOutputId previousTransactionOutputId = transactionOutputDatabaseManager.findTransactionOutput(TransactionOutputIdentifier.fromTransactionInput(transactionInput));
        return _findTransactionInputId(transactionId, previousTransactionOutputId);
    }

    public TransactionInputId insertTransactionInput(final TransactionId transactionId, final TransactionInput transactionInput) throws DatabaseException {
        return _insertTransactionInput(transactionId, transactionInput);
    }

    public List<TransactionInputId> insertTransactionInputs(final Map<Sha256Hash, TransactionId> transactionIds, final List<Transaction> transactions) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getTransactionOutputDatabaseManager();

        if (! Util.areEqual(transactionIds.size(), transactions.getSize())) {
            Logger.log("NOTICE: Error storing TransactionInputs. Parameter mismatch: expected " + transactionIds.size() + ", got " + transactions.getSize());
            return null;
        }
        if (transactions.isEmpty()) { return new MutableList<TransactionInputId>(0); }

        // final MilliTimer findPreviousTransactionsTimer = new MilliTimer();
        final NanoTimer findPreviousTxOutputTimer = new NanoTimer();
        final MilliTimer txInputPrepareInsertQueryTimer = new MilliTimer();
        final MilliTimer insertTxInputTimer = new MilliTimer();
        final MilliTimer insertUnlockingScriptsTimer = new MilliTimer();
        final MilliTimer markOutputsAsSpentTimer = new MilliTimer();
        double totalFindPreviousTxOutputTime = 0D;

        final int transactionCount = transactions.getSize();

        final Query batchedInsertQuery = new BatchedInsertQuery("INSERT INTO transaction_inputs (transaction_id, previous_transaction_output_id, sequence_number) VALUES (?, ?, ?)");

        final MutableList<UnlockingScript> unlockingScripts = new MutableList<UnlockingScript>(transactionCount * 2);

        final MutableList<TransactionOutputId> newlySpentTransactionOutputIds = new MutableList<TransactionOutputId>(transactionCount * 2);
        final MutableList<TransactionOutputIdentifier> newlySpentTransactionOutputIdentifiers = new MutableList<TransactionOutputIdentifier>(transactionCount * 2);

//        findPreviousTxOutputTimer.start();
//        final Map<TransactionOutputIdentifier, TransactionOutputId> previousTransactionOutputsMap = transactionOutputDatabaseManager.getPreviousTransactionOutputs(transactions);
//        if (previousTransactionOutputsMap == null) { return null; }
//        findPreviousTxOutputTimer.stop();

        txInputPrepareInsertQueryTimer.start();
        int transactionInputIdCount = 0;
        for (int i = 0; i < transactionCount; ++i) {
            final Transaction transaction = transactions.get(i);
            final Sha256Hash transactionHash = transaction.getHash();
            if (! transactionIds.containsKey(transactionHash)) {
                Logger.log("NOTICE: Error storing TransactionInputs. Missing Transaction: " + transactionHash);
                return null;
            }
            final TransactionId transactionId = transactionIds.get(transactionHash);
            final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();

            for (final TransactionInput transactionInput : transactionInputs) {
                final UnlockingScript unlockingScript = transactionInput.getUnlockingScript();
                unlockingScripts.add(unlockingScript);

                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionInput.getPreviousOutputTransactionHash(), transactionInput.getPreviousOutputIndex());
                final Boolean isCoinbase = Util.areEqual(Sha256Hash.EMPTY_HASH, transactionInput.getPreviousOutputTransactionHash());

                // final TransactionOutputId previousTransactionOutputId = previousTransactionOutputsMap.get(transactionOutputIdentifier);
                final TransactionOutputId previousTransactionOutputId;
                if (isCoinbase) {
                    previousTransactionOutputId = null;
                }
                else {
                    findPreviousTxOutputTimer.start();
                    previousTransactionOutputId = transactionOutputDatabaseManager.findTransactionOutput(transactionOutputIdentifier);
                    findPreviousTxOutputTimer.stop();
                    totalFindPreviousTxOutputTime += findPreviousTxOutputTimer.getMillisecondsElapsed();
                    if (previousTransactionOutputId == null) {
                        Logger.log("Unable to find TransactionOutput: " + transactionOutputIdentifier.getTransactionHash() + ":"+ transactionOutputIdentifier.getOutputIndex());
                        return null;
                    }
                }

                if (previousTransactionOutputId != null) { // Should only true for the coinbase input...
                    newlySpentTransactionOutputIds.add(previousTransactionOutputId);
                    newlySpentTransactionOutputIdentifiers.add(transactionOutputIdentifier);
                }

                batchedInsertQuery.setParameter(transactionId);
                batchedInsertQuery.setParameter(previousTransactionOutputId);
                batchedInsertQuery.setParameter(transactionInput.getSequenceNumber());

                transactionInputIdCount += 1;
            }
        }
        txInputPrepareInsertQueryTimer.stop();

        insertTxInputTimer.start();
        final Long firstTransactionInputId = databaseConnection.executeSql(batchedInsertQuery);
        if (firstTransactionInputId == null) {
            Logger.log("NOTICE: Error storing TransactionInputs. Error running batch insert.");
            return null;
        }
        insertTxInputTimer.stop();

        final MutableList<TransactionInputId> transactionInputIds = new MutableList<TransactionInputId>(transactionInputIdCount);
        for (int i = 0; i < transactionInputIdCount; ++i) {
            final TransactionInputId transactionInputId = TransactionInputId.wrap(firstTransactionInputId + i);
            transactionInputIds.add(transactionInputId);
        }

        insertUnlockingScriptsTimer.start();
        _insertUnlockingScripts(transactionInputIds, unlockingScripts);
        insertUnlockingScriptsTimer.stop();

        markOutputsAsSpentTimer.start();
        transactionOutputDatabaseManager.markTransactionOutputsAsSpent(newlySpentTransactionOutputIds, newlySpentTransactionOutputIdentifiers);
        markOutputsAsSpentTimer.stop();

        // Logger.log("findPreviousTransactionsTimer: " + findPreviousTransactionsTimer.getMillisecondsElapsed() + "ms");
        // Logger.log("findPreviousTxOutputTimer: " + findPreviousTxOutputTimer.getMillisecondsElapsed() + "ms");
        Logger.log("findPreviousTxOutputTimer: " + totalFindPreviousTxOutputTime + "ms");
        Logger.log("txInputPrepareInsertQueryTimer: " + txInputPrepareInsertQueryTimer.getMillisecondsElapsed() + "ms");
        Logger.log("insertTxInputTimer: " + insertTxInputTimer.getMillisecondsElapsed() + "ms");
        Logger.log("insertUnlockingScriptsTimer: " + insertUnlockingScriptsTimer.getMillisecondsElapsed() + "ms");
        Logger.log("markOutputsAsSpentTimer: " + markOutputsAsSpentTimer.getMillisecondsElapsed() + "ms");

        /*
            [TransactionInputDatabaseManager.java:262] findPreviousTransactionsTimer: 999ms
            [TransactionInputDatabaseManager.java:263] findPreviousTxOutputTimer: 2166ms
            [TransactionInputDatabaseManager.java:264] txInputPrepareInsertQueryTimer: 6ms
            [TransactionInputDatabaseManager.java:265] insertTxInputTimer: 1072ms
            [TransactionInputDatabaseManager.java:266] insertUnlockingScriptsTimer: 383ms
            [TransactionInputDatabaseManager.java:267] markOutputsAsSpentTimer: 946ms
            [TransactionDatabaseManager.java:360] selectTransactionHashesTimer: 783ms
            [TransactionDatabaseManager.java:361] storeTransactionRecordsTimer: 451ms
            [TransactionDatabaseManager.java:362] insertTransactionOutputsTimer: 773ms
            [TransactionDatabaseManager.java:363] InsertTransactionInputsTimer: 5572ms
            [BlockDatabaseManager.java:43] AssociateTransactions: 474ms
            [BlockDatabaseManager.java:52] StoreBlockDuration: 8053ms
            [BlockProcessor.java:145] Stored 2219 transactions in 8053.57ms (275.53 tps). 00000000000000000DEB5D7B988DF7455189928DF719326DE18EC3D267778AE2
        */

        return transactionInputIds;
    }

    public TransactionInput getTransactionInput(final TransactionInputId transactionInputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> transactionInputRows = databaseConnection.query(
            new Query("SELECT * FROM transaction_inputs WHERE id = ?")
                .setParameter(transactionInputId)
        );
        if (transactionInputRows.isEmpty()) { return null; }

        final Row transactionInputRow = transactionInputRows.get(0);

        final MutableTransactionInput transactionInput = new MutableTransactionInput();

        final Sha256Hash previousOutputTransactionHash;
        final Integer previousOutputIndex;
        {
            final java.util.List<Row> previousOutputTransactionRows = databaseConnection.query(
                new Query("SELECT transaction_outputs.id, transactions.hash, transaction_outputs.`index` FROM transaction_outputs INNER JOIN transactions ON (transaction_outputs.transaction_id = transactions.id) WHERE transaction_outputs.id = ?")
                    .setParameter(transactionInputRow.getLong("previous_transaction_output_id"))
            );
            if (previousOutputTransactionRows.isEmpty()) {
                previousOutputTransactionHash = new ImmutableSha256Hash();
                previousOutputIndex = -1;
            }
            else {
                final Row previousOutputTransactionRow = previousOutputTransactionRows.get(0);
                previousOutputTransactionHash = Sha256Hash.fromHexString(previousOutputTransactionRow.getString("hash"));
                previousOutputIndex = previousOutputTransactionRow.getInteger("index");
            }
        }

        final UnlockingScript unlockingScript;
        {
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT id, script FROM unlocking_scripts WHERE transaction_input_id = ?")
                    .setParameter(transactionInputId)
            );
            if (rows.isEmpty()) { return null; }

            final Row row = rows.get(0);
            unlockingScript = new MutableUnlockingScript(MutableByteArray.wrap(row.getBytes("script")));
        }

        final SequenceNumber sequenceNumber = new ImmutableSequenceNumber(transactionInputRow.getLong("sequence_number"));

        transactionInput.setPreviousOutputTransactionHash(previousOutputTransactionHash);
        transactionInput.setPreviousOutputIndex(previousOutputIndex);

        transactionInput.setUnlockingScript(unlockingScript);
        transactionInput.setSequenceNumber(sequenceNumber);

        return transactionInput;
    }

    public TransactionOutputId findPreviousTransactionOutputId(final TransactionInput transactionInput) throws DatabaseException {
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getTransactionOutputDatabaseManager();
        return transactionOutputDatabaseManager.findTransactionOutput(TransactionOutputIdentifier.fromTransactionInput(transactionInput));
    }

    public TransactionOutputId getPreviousTransactionOutputId(final TransactionInputId transactionInputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, previous_transaction_output_id FROM transaction_inputs WHERE id = ?")
                .setParameter(transactionInputId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionOutputId.wrap(row.getLong("previous_transaction_output_id"));
    }

    public List<TransactionInputId> getTransactionInputIds(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM transaction_inputs WHERE transaction_id = ? ORDER BY id ASC")
                .setParameter(transactionId)
        );

        final ImmutableListBuilder<TransactionInputId> transactionInputIds = new ImmutableListBuilder<TransactionInputId>(rows.size());
        for (final Row row : rows) {
            final TransactionInputId transactionInputId = TransactionInputId.wrap(row.getLong("id"));
            transactionInputIds.add(transactionInputId);
        }
        return transactionInputIds.build();
    }

    public TransactionId getPreviousTransactionId(final TransactionInputId transactionInputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT transaction_outputs.transaction_id FROM transaction_inputs INNER JOIN transaction_outputs ON transaction_inputs.previous_transaction_output_id = transaction_outputs.id WHERE transaction_inputs.id = ?")
                .setParameter(transactionInputId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionId.wrap(row.getLong("transaction_id"));
    }

    public void updateTransactionInput(final TransactionInputId transactionInputId, final TransactionId transactionId, final TransactionInput transactionInput) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getTransactionOutputDatabaseManager();

        final TransactionOutputId previousTransactionOutputId;
        {
            if (Util.areEqual(Sha256Hash.EMPTY_HASH, transactionInput.getPreviousOutputTransactionHash())) {
                previousTransactionOutputId = null;
            }
            else {
                previousTransactionOutputId = transactionOutputDatabaseManager.findTransactionOutput(TransactionOutputIdentifier.fromTransactionInput(transactionInput));
                if (previousTransactionOutputId == null) {
                    throw new DatabaseException("Could not find TransactionInput.previousOutputTransaction: " + transactionId + " " + transactionInput.getPreviousOutputIndex() + ":" + transactionInput.getPreviousOutputTransactionHash());
                }
            }
        }

        final UnlockingScript unlockingScript = transactionInput.getUnlockingScript();

        databaseConnection.executeSql(
            new Query("UPDATE transaction_inputs SET transaction_id = ?, previous_transaction_output_id = ?, sequence_number = ? WHERE id = ?")
                .setParameter(transactionId)
                .setParameter(previousTransactionOutputId)
                .setParameter(transactionInput.getSequenceNumber())
                .setParameter(transactionInputId)
        );

        // NOTE: The original PreviousTransactionOutputId should not be unmarked because it is possible it is still being spent by another transaction.
        //  While keeping this TransactionOutput marked as spent may lead to an unspent TransactionOutput being marked as spent it is fairly safe
        //  since this method is a performance improvement more so than a true representation of state.
        if (previousTransactionOutputId != null) {
            final TransactionOutputIdentifier previousOutputTransactionOutputIdentifier = new TransactionOutputIdentifier(transactionInput.getPreviousOutputTransactionHash(), transactionInput.getPreviousOutputIndex());
            transactionOutputDatabaseManager.markTransactionOutputAsSpent(previousTransactionOutputId, previousOutputTransactionOutputIdentifier);
        }

        _updateUnlockingScript(transactionInputId, unlockingScript);
    }

    public void deleteTransactionInput(final TransactionInputId transactionInputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        databaseConnection.executeSql(
            new Query("DELETE FROM unlocking_scripts WHERE transaction_input_id = ?")
                .setParameter(transactionInputId)
        );

        databaseConnection.executeSql(
            new Query("DELETE FROM transaction_inputs WHERE id = ?")
                .setParameter(transactionInputId)
        );
    }

    public TransactionId getTransactionId(final TransactionInputId transactionInputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, transaction_id FROM transaction_inputs WHERE id = ?")
                .setParameter(transactionInputId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionId.wrap(row.getLong("transaction_id"));
    }

    public List<TransactionInputId> getTransactionInputIdsSpendingTransactionOutput(final TransactionOutputId transactionOutputId) throws  DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM transaction_inputs WHERE previous_transaction_output_id = ?")
                .setParameter(transactionOutputId)
        );

        final MutableList<TransactionInputId> transactionInputIds = new MutableList<TransactionInputId>(rows.size());
        for (final Row row : rows) {
            final TransactionInputId transactionInputId = TransactionInputId.wrap(row.getLong("id"));
            transactionInputIds.add(transactionInputId);
        }
        return transactionInputIds;
    }
}
