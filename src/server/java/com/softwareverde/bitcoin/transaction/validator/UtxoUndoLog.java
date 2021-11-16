package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.ImmutableUnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.UnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

import java.util.HashMap;
import java.util.HashSet;

public class UtxoUndoLog {
    protected final FullNodeDatabaseManager _databaseManager;
    protected final HashMap<TransactionOutputIdentifier, UnspentTransactionOutput> _availableOutputs = new HashMap<>();
    protected final HashSet<TransactionOutputIdentifier> _unavailableOutputs = new HashSet<>();

    public UtxoUndoLog(final FullNodeDatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    public void undoBlock(final Block block) throws DatabaseException {
        Logger.debug("Undoing Block: " + block.getHash());
        final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = _databaseManager.getUnspentTransactionOutputDatabaseManager();

        final List<Transaction> transactions = block.getTransactions();
        boolean isCoinbase = true;
        for (final Transaction transaction : transactions) {
            if (! isCoinbase) {
                final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
                for (final TransactionInput transactionInput : transactionInputs) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                    final UnspentTransactionOutput unspentTransactionOutput = unspentTransactionOutputDatabaseManager.findOutputData(transactionOutputIdentifier);
                    if (unspentTransactionOutput == null) {
                        throw new DatabaseException("Unable to find Output: " + transactionOutputIdentifier);
                    }

                    _availableOutputs.put(transactionOutputIdentifier, unspentTransactionOutput);
                }
            }

            final List<TransactionOutputIdentifier> transactionOutputIdentifiers = TransactionOutputIdentifier.fromTransactionOutputs(transaction);
            for (final TransactionOutputIdentifier transactionOutputIdentifier : transactionOutputIdentifiers) {
                _unavailableOutputs.add(transactionOutputIdentifier);
            }

            isCoinbase = false;
        }
    }

    public void applyBlock(final Block block, final Long blockHeight) {
        Logger.debug("Applying Block: " + block.getHash());
        final List<Transaction> transactions = block.getTransactions();
        boolean isCoinbase = true;
        for (final Transaction transaction : transactions) {
            if (! isCoinbase) {
                final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
                for (final TransactionInput transactionInput : transactionInputs) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                    _availableOutputs.remove(transactionOutputIdentifier);
                }
            }

            final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
            final List<TransactionOutputIdentifier> transactionOutputIdentifiers = TransactionOutputIdentifier.fromTransactionOutputs(transaction);
            for (final TransactionOutputIdentifier transactionOutputIdentifier : transactionOutputIdentifiers) {
                final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();
                final TransactionOutput transactionOutput = transactionOutputs.get(outputIndex);

                final UnspentTransactionOutput unspentTransactionOutput = new ImmutableUnspentTransactionOutput(transactionOutput, blockHeight, isCoinbase);

                _unavailableOutputs.remove(transactionOutputIdentifier);
                _availableOutputs.put(transactionOutputIdentifier, unspentTransactionOutput);
            }

            isCoinbase = false;
        }
    }

    public UnspentTransactionOutput getUnspentTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        if (_unavailableOutputs.contains(transactionOutputIdentifier)) { return null; }

        final UnspentTransactionOutput reAvailableOutput = _availableOutputs.get(transactionOutputIdentifier);
        if (reAvailableOutput != null) { return reAvailableOutput; }

        final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = _databaseManager.getUnspentTransactionOutputDatabaseManager();
        try {
            return unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(transactionOutputIdentifier);
        }
        catch (final Exception exception) {
            Logger.debug(exception);
            return null;
        }
    }
}
