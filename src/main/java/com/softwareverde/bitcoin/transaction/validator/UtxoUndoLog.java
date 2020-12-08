package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

import java.util.HashMap;
import java.util.HashSet;

public class UtxoUndoLog {
    protected final FullNodeDatabaseManager _databaseManager;
    protected final HashMap<TransactionOutputIdentifier, TransactionOutput> _reAvailableOutputs = new HashMap<TransactionOutputIdentifier, TransactionOutput>();
    protected final HashSet<TransactionOutputIdentifier> _uncreatedOutputs = new HashSet<TransactionOutputIdentifier>();

    public UtxoUndoLog(final FullNodeDatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    public void undoBlock(final Block block) throws DatabaseException {
        Logger.debug("Undoing Block: " + block.getHash());
        final FullNodeTransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();

        final List<Transaction> transactions = block.getTransactions();
        boolean isCoinbase = true;
        for (final Transaction transaction : transactions) {
            if (! isCoinbase) {
                final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
                for (final TransactionInput transactionInput : transactionInputs) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                    final TransactionOutput transactionOutput = transactionDatabaseManager.getTransactionOutput(transactionOutputIdentifier);
                    _reAvailableOutputs.put(transactionOutputIdentifier, transactionOutput);
                }
            }

            final List<TransactionOutputIdentifier> transactionOutputIdentifiers = TransactionOutputIdentifier.fromTransactionOutputs(transaction);
            for (final TransactionOutputIdentifier transactionOutputIdentifier : transactionOutputIdentifiers) {
                _uncreatedOutputs.add(transactionOutputIdentifier);
            }

            isCoinbase = false;
        }
    }

    public void redoBlock(final Block block) {
        Logger.debug("Redoing Block: " + block.getHash());
        final List<Transaction> transactions = block.getTransactions();
        boolean isCoinbase = true;
        for (final Transaction transaction : transactions) {
            if (! isCoinbase) {
                final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
                for (final TransactionInput transactionInput : transactionInputs) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                    _reAvailableOutputs.remove(transactionOutputIdentifier);
                }
            }

            final List<TransactionOutputIdentifier> transactionOutputIdentifiers = TransactionOutputIdentifier.fromTransactionOutputs(transaction);
            for (final TransactionOutputIdentifier transactionOutputIdentifier : transactionOutputIdentifiers) {
                _uncreatedOutputs.remove(transactionOutputIdentifier);
            }

            isCoinbase = false;
        }
    }

    public TransactionOutput getUnspentTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        if (_uncreatedOutputs.contains(transactionOutputIdentifier)) { return null; }

        final TransactionOutput reAvailableOutput = _reAvailableOutputs.get(transactionOutputIdentifier);
        if (reAvailableOutput != null) { return reAvailableOutput; }

        try {
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();
            return transactionDatabaseManager.getTransactionOutput(transactionOutputIdentifier);
        }
        catch (final Exception exception) {
            Logger.debug(exception);
            return null;
        }
    }
}
