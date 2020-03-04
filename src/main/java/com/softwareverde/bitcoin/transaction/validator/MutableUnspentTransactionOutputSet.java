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
import com.softwareverde.security.hash.sha256.Sha256Hash;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class MutableUnspentTransactionOutputSet implements UnspentTransactionOutputSet {
    protected Map<TransactionOutputIdentifier, TransactionOutput> _cachedTransactionOutputs;

    /**
     * Loads all outputs spent by the provided block.
     *  Returns true if all of the outputs were found, and false if at least one output could not be found.
     *  Outputs may not be found in the case of an invalid block, but also if its predecessor has not been validated yet.
     */
    public synchronized Boolean loadOutputsForBlock(final FullNodeDatabaseManager databaseManager, final Block block) {
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
                    if (! isUnique) { // Two inputs cannot spent the same output...
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

        boolean loadedAllOutputsSuccessfully = true;
        final Map<TransactionOutputIdentifier, TransactionOutput> cachedTransactionOutputs = new HashMap<TransactionOutputIdentifier, TransactionOutput>(transactionCount);
        try {
            final List<TransactionOutputIdentifier> transactionOutputIdentifiers = new MutableList<TransactionOutputIdentifier>(requiredTransactionOutputs);
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final List<TransactionOutput> transactionOutputs = transactionDatabaseManager.getUnspentTransactionOutputs(transactionOutputIdentifiers);
            for (int i = 0; i < transactionOutputs.getCount(); ++i) {
                final TransactionOutputIdentifier transactionOutputIdentifier = transactionOutputIdentifiers.get(i);
                final TransactionOutput transactionOutput = transactionOutputs.get(i);
                if (transactionOutput == null) {
                    loadedAllOutputsSuccessfully = false;
                    continue; // Continue processing for pre-loading the UTXO set for pending blocks...
                }

                cachedTransactionOutputs.put(transactionOutputIdentifier, transactionOutput);
            }
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return false;
        }

        _cachedTransactionOutputs = cachedTransactionOutputs;
        return loadedAllOutputsSuccessfully;
    }

    @Override
    public TransactionOutput getUnspentTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return _cachedTransactionOutputs.get(transactionOutputIdentifier);
    }

    /**
     * Adds new outputs created by the provided block and removes outputs spent by the block.
     */
    public synchronized void update(Block block) {
        if (_cachedTransactionOutputs == null) {
            _cachedTransactionOutputs = new HashMap<TransactionOutputIdentifier, TransactionOutput>();
        }

        final List<Transaction> transactions = block.getTransactions();

        { // Add the new outputs created by the block...
            for (final Transaction transaction : transactions) {
                final Sha256Hash transactionHash = transaction.getHash();

                int outputIndex = 0;
                final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
                for (final TransactionOutput transactionOutput : transactionOutputs) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
                    _cachedTransactionOutputs.put(transactionOutputIdentifier, transactionOutput);
                    outputIndex += 1;
                }
            }
        }

        { // Remove the spent PreviousTransactionOutputs from the list of outputs to retrieve...
            for (final Transaction transaction : transactions) {
                final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
                for (final TransactionInput transactionInput : transactionInputs) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                    _cachedTransactionOutputs.remove(transactionOutputIdentifier);
                }
            }
        }
    }
}
