package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionInputDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.runner.Context;
import com.softwareverde.bitcoin.transaction.script.runner.ScriptRunner;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;

import java.util.HashMap;
import java.util.Map;

public class BlockValidator {
    protected final BlockDatabaseManager _blockDatabaseManager;
    protected final TransactionDatabaseManager _transactionDatabaseManager;
    protected final TransactionOutputDatabaseManager _transactionOutputDatabaseManager;
    protected final TransactionInputDatabaseManager _transactionInputDatabaseManager;

    public static class TransactionOutputIdentifier {
        protected final Hash _transactionHash;
        protected final Integer _outputIndex;

        public TransactionOutputIdentifier(final Hash transactionHash, final Integer outputIndex) {
            _transactionHash = new ImmutableHash(transactionHash);
            _outputIndex = outputIndex;
        }

        public Hash getTransactionHash() {
            return _transactionHash;
        }

        public Integer getOutputIndex() {
            return _outputIndex;
        }
    }

    protected TransactionOutput _findTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        try {
            final Integer transactionOutputIndex = transactionOutputIdentifier.getOutputIndex();
            final Long transactionId = _transactionDatabaseManager.getTransactionIdFromHash(transactionOutputIdentifier.getTransactionHash());
            if (transactionId == null) { return null; }

            final Long transactionOutputId = _transactionOutputDatabaseManager.findTransactionOutput(transactionId, transactionOutputIndex);
            if (transactionOutputId == null) { return null; }

            return _transactionOutputDatabaseManager.getTransactionOutput(transactionOutputId);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    protected Long _calculateTotalTransactionInputs(final Transaction blockTransaction, final List<Transaction> queuedTransactions) {
        final Map<Hash, Transaction> additionalTransactionOutputs = new HashMap<Hash, Transaction>();
        for (final Transaction transaction : queuedTransactions) {
            additionalTransactionOutputs.put(transaction.getHash(), transaction);
        }

        long totalInputValue = 0L;
        final List<TransactionInput> transactionInputs = blockTransaction.getTransactionInputs();
        for (final TransactionInput transactionInput : transactionInputs) {
            final Hash outputTransactionHash = transactionInput.getPreviousTransactionOutputHash();
            final Integer transactionOutputIndex = transactionInput.getPreviousTransactionOutputIndex();

            final TransactionOutput transactionOutput;
            {
                TransactionOutput possibleTransactionOutput = _findTransactionOutput(new TransactionOutputIdentifier(outputTransactionHash, transactionOutputIndex));
                if (possibleTransactionOutput == null) {
                    final Transaction transactionContainingOutput = additionalTransactionOutputs.get(outputTransactionHash);
                    if (transactionContainingOutput != null) {
                        final List<TransactionOutput> transactionOutputs = transactionContainingOutput.getTransactionOutputs();
                        if (transactionOutputIndex < transactionOutputs.getSize()) {
                            possibleTransactionOutput = transactionOutputs.get(transactionOutputIndex);
                        }
                    }
                }
                transactionOutput = possibleTransactionOutput;
            }
            if (transactionOutput == null) {
                Logger.log("Tx Input, Output Not Found: " + BitcoinUtil.toHexString(outputTransactionHash) + ":" + transactionOutputIndex + ", for Tx: "+ blockTransaction.getHash());
                return null;
            }

            totalInputValue += transactionOutput.getAmount();
        }
        return totalInputValue;
    }

    protected Boolean _validateTransactionExpenditure(final Transaction blockTransaction, final List<Transaction> queuedTransactions) {
        final Long totalOutputValue = blockTransaction.getTotalOutputValue();
        final Long totalInputValue = _calculateTotalTransactionInputs(blockTransaction, queuedTransactions);
        if (totalInputValue == null) { return false; }

        return (totalOutputValue <= totalInputValue);
    }

    protected Boolean _validateTransactionInputsAreUnlocked(final Transaction transaction) {
        final ScriptRunner scriptRunner = new ScriptRunner();

        final Context context = new Context();
        context.setTransaction(transaction);

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        for (int i=0; i<transactionInputs.getSize(); ++i) {
            final TransactionInput transactionInput = transactionInputs.get(i);
            final TransactionOutput transactionOutput = _findTransactionOutput(new TransactionOutputIdentifier(transactionInput.getPreviousTransactionOutputHash(), transactionInput.getPreviousTransactionOutputIndex()));
            if (transactionOutput == null) { return false; }

            final Script lockingScript = transactionOutput.getLockingScript();
            final Script unlockingScript = transactionInput.getUnlockingScript();

            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(transactionOutput);
            context.setTransactionInputIndex(i);

            final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);
            if (! inputIsUnlocked) { return false; }
        }

        return true;
    }

    public BlockValidator(final MysqlDatabaseConnection databaseConnection) {
        _blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        _transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection);
        _transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(databaseConnection);
        _transactionInputDatabaseManager = new TransactionInputDatabaseManager(databaseConnection);

    }

    public Boolean validateBlock(final Block block) {
        if (! block.isValid()) { return false; }

        final BlockDeflater blockDeflater = new BlockDeflater();

        final List<Transaction> blockTransactions = block.getTransactions();
        for (int i=0; i<blockTransactions.getSize(); ++i) {
            if (i == 0) { continue; } // TODO: The coinbase transaction requires a separate validation process...

            final Transaction blockTransaction = blockTransactions.get(i);
            final Boolean transactionExpenditureIsValid = _validateTransactionExpenditure(blockTransaction, blockTransactions);
            if (! transactionExpenditureIsValid) {
                Logger.log("BLOCK VALIDATION: Failed because expenditures did not match.");
                Logger.log(BitcoinUtil.toHexString(blockDeflater.toBytes(block)));
                return false;
            }

            final Boolean transactionInputsAreUnlocked = _validateTransactionInputsAreUnlocked(blockTransaction);
            if (! transactionInputsAreUnlocked) {
                Logger.log("BLOCK VALIDATION: Failed because of invalid transaction.");
                Logger.log(BitcoinUtil.toHexString(blockDeflater.toBytes(block)));
                return false;
            }
        }

        return true;
    }
}
