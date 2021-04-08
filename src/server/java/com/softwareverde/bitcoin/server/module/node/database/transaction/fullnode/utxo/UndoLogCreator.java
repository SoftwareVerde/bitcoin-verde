package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;

import java.util.HashMap;
import java.util.Map;

public class UndoLogCreator {
    public static final Integer MAX_REORG_DEPTH = 256;

    public void createUndoLog(final Long blockHeight, final Block block, final UnspentTransactionOutputContext unspentTransactionOutputContext, final DatabaseConnection databaseConnection) throws DatabaseException {
        final HashMap<TransactionOutputIdentifier, TransactionOutput> transactionOutputIdentifiers = new HashMap<>();
        for (final Transaction transaction : block.getTransactions()) {
            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                final TransactionOutput unspentTransactionOutput = unspentTransactionOutputContext.getTransactionOutput(transactionOutputIdentifier);
                transactionOutputIdentifiers.put(transactionOutputIdentifier, unspentTransactionOutput);
            }
        }

        final Long expiresAfterBlockHeight = (blockHeight + UndoLogCreator.MAX_REORG_DEPTH);

        final Query query = new Query("INSERT INTO pruned_previous_transaction_outputs (transaction_hash, `index`, expires_after_block_height, amount, locking_script) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE expires_after_block_height = GREATEST(VALUES(expires_after_block_height), expires_after_block_height);");
        for (Map.Entry<TransactionOutputIdentifier, TransactionOutput> transactionOutputEntry : transactionOutputIdentifiers.entrySet()) {
            final TransactionOutputIdentifier transactionOutputIdentifier = transactionOutputEntry.getKey();
            final TransactionOutput transactionOutput = transactionOutputEntry.getValue();

            final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
            final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

            final Long amount = transactionOutput.getAmount();
            final LockingScript lockingScript = transactionOutput.getLockingScript();

            query.setParameter(transactionHash);
            query.setParameter(outputIndex);
            query.setParameter(expiresAfterBlockHeight);
            query.setParameter(amount);
            query.setParameter(lockingScript.getBytes());
        }

        databaseConnection.executeSql(query);
    }
}
