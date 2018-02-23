package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.runner.ScriptRunner;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;

import java.util.List;

public class BlockValidator {
    protected final BlockDatabaseManager _blockDatabaseManager;
    protected final TransactionDatabaseManager _transactionDatabaseManager;

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
            final Long transactionOutputId = _transactionDatabaseManager.getTransactionIdFromHash(transactionOutputIdentifier.getTransactionHash());
            if (transactionOutputId == null) { return null; }

            return _transactionDatabaseManager.findTransactionOutput(transactionOutputId, transactionOutputIndex);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    protected Long _calculateTotalTransactionInputs(final Transaction blockTransaction) {
        long totalInputValue = 0L;
        final List<TransactionInput> transactionInputs = blockTransaction.getTransactionInputs();
        for (final TransactionInput transactionInput : transactionInputs) {
            final Hash transactionInputOutputHash = transactionInput.getPreviousTransactionOutput();
            final Integer transactionInputOutputIndex = transactionInput.getPreviousTransactionOutputIndex();
            final TransactionOutput transactionOutput = _findTransactionOutput(new TransactionOutputIdentifier(transactionInputOutputHash, transactionInputOutputIndex));
            if (transactionOutput == null) {
                Logger.log("Validate Tx: Unable to find transaction: "+ BitcoinUtil.toHexString(transactionInputOutputHash) +":"+ transactionInputOutputIndex);
                return null;
            }

            totalInputValue += transactionOutput.getAmount();
        }
        return totalInputValue;
    }

    protected Boolean _validateTransactionExpenditure(final Transaction blockTransaction) {
        final Long totalOutputValue = blockTransaction.getTotalOutputValue();
        final Long totalInputValue = _calculateTotalTransactionInputs(blockTransaction);
        Logger.log("(Inputs: "+ totalInputValue+"; Outputs: "+ totalOutputValue +")");
        if (totalInputValue == null) { return false; }

        return (totalOutputValue <= totalInputValue);
    }

    protected Boolean _validateTransactionInputsAreUnlocked(final Transaction transaction) {
        final ScriptRunner scriptRunner = new ScriptRunner();
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final TransactionOutput transactionOutput = _findTransactionOutput(new TransactionOutputIdentifier(transactionInput.getPreviousTransactionOutput(), transactionInput.getPreviousTransactionOutputIndex()));
            if (transactionOutput == null) { return false; }

            final Script lockingScript = transactionOutput.getLockingScript();
            final Script unlockingScript = transactionInput.getUnlockingScript();
            final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript);
            if (! inputIsUnlocked) { return false; }
        }

        return true;
    }

    public BlockValidator(final MysqlDatabaseConnection databaseConnection) {
        _blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        _transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection);

    }

    public Boolean validateBlock(final Block block) {
        if (! block.validateBlockHeader()) { return false; }

        final List<Transaction> blockTransactions = block.getTransactions();
        for (int i=0; i<blockTransactions.size(); ++i) {
            if (i == 0) { continue; } // TODO: The coinbase transaction requires a separate validation process...

            final Transaction blockTransaction = blockTransactions.get(i);
            final Boolean transactionExpenditureIsValid = _validateTransactionExpenditure(blockTransaction);
            if (! transactionExpenditureIsValid) { return false; }

            final Boolean transactionInputsAreUnlocked = _validateTransactionInputsAreUnlocked(blockTransaction);
            if (! transactionInputsAreUnlocked) { return false; }
        }

        return true;
    }
}
