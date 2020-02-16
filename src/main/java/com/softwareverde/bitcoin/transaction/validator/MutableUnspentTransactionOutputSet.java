package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class MutableUnspentTransactionOutputSet implements UnspentTransactionOutputSet {
    protected Map<TransactionOutputIdentifier, TransactionOutput> _cachedTransactionOutputs;

    public Boolean loadOutputsForBlock(final FullNodeDatabaseManager databaseManager, final Block block) {
        final List<Transaction> transactions = block.getTransactions();
        final int transactionCount = (transactions.getCount() - 1); // Exclude coinbase...

        Transaction coinbaseTransaction = null;
        final HashSet<TransactionOutputIdentifier> requiredTransactionOutputs = new HashSet<TransactionOutputIdentifier>();
        final HashSet<TransactionOutputIdentifier> newOutputs = new HashSet<TransactionOutputIdentifier>();
        for (final Transaction transaction : transactions) {
            if (coinbaseTransaction == null) { // Skip the coinbase transaction...
                coinbaseTransaction = transaction;
                continue;
            }

            { // Add the PreviousTransactionOutputs to the list of outputs to retrieve...
                final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
                for (final TransactionInput transactionInput : transactionInputs) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                    final boolean isUnique = requiredTransactionOutputs.add(transactionOutputIdentifier);
                    if (! isUnique) { // Two inputs cannot same the same output...
                        return false;
                    }
                }
            }

            { // Catalogue the new outputs that are created in this block to prevent loading them from disk...
                final List<TransactionOutputIdentifier> outputIdentifiers = TransactionOutputIdentifier.fromTransactionOutputs(transaction);
                for (final TransactionOutputIdentifier transactionOutputIdentifier : outputIdentifiers) {
                    newOutputs.add(transactionOutputIdentifier);
                }
            }
        }

        requiredTransactionOutputs.removeAll(newOutputs); // New outputs created by this block are not added to this UTXO set.

        final Map<TransactionOutputIdentifier, TransactionOutput> cachedTransactionOutputs = new HashMap<TransactionOutputIdentifier, TransactionOutput>(transactionCount);
        try {
            final List<TransactionOutputIdentifier> transactionOutputIdentifiers = new MutableList<TransactionOutputIdentifier>(requiredTransactionOutputs);
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final List<TransactionOutput> transactionOutputs = transactionDatabaseManager.getUnspentTransactionOutputs(transactionOutputIdentifiers);
            for (int i = 0; i < transactionOutputs.getCount(); ++i) {
                final TransactionOutputIdentifier transactionOutputIdentifier = transactionOutputIdentifiers.get(i);
                final TransactionOutput transactionOutput = transactionOutputs.get(i);
                if (transactionOutput == null) {
                    Logger.debug("Could not load output from database: " + transactionOutputIdentifier);
                    return false;
                }

                cachedTransactionOutputs.put(transactionOutputIdentifier, transactionOutput);
            }
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return false;
        }

        _cachedTransactionOutputs = cachedTransactionOutputs;
        return true;
    }

    @Override
    public TransactionOutput getUnspentTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return _cachedTransactionOutputs.get(transactionOutputIdentifier);
    }

    public void addBlock(Block block) {
        _cachedTransactionOutputs = new HashMap<TransactionOutputIdentifier, TransactionOutput>(0);

//        final List<Transaction> transactions = block.getTransactions();
//        for (final Transaction transaction : transactions) {
//
//            { // Remove the spent PreviousTransactionOutputs from the list of outputs to retrieve...
//                final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
//                for (final TransactionInput transactionInput : transactionInputs) {
//                    final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
//                    _cachedTransactionOutputs.remove(transactionOutputIdentifier);
//                }
//            }
//        }
    }
}
