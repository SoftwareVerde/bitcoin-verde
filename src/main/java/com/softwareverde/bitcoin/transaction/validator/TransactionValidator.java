package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.server.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionInputDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.runner.Context;
import com.softwareverde.bitcoin.transaction.script.runner.ScriptRunner;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;

public class TransactionValidator {
    protected final TransactionDatabaseManager _transactionDatabaseManager;
    protected final TransactionOutputDatabaseManager _transactionOutputDatabaseManager;
    protected final TransactionInputDatabaseManager _transactionInputDatabaseManager;

    protected TransactionOutput _findTransactionOutput(final BlockValidator.TransactionOutputIdentifier transactionOutputIdentifier) {
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

    public TransactionValidator(final MysqlDatabaseConnection databaseConnection) {
        _transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection);
        _transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(databaseConnection);
        _transactionInputDatabaseManager = new TransactionInputDatabaseManager(databaseConnection);
    }

    public Boolean validateTransactionInputsAreUnlocked(final Transaction transaction) {
        final ScriptRunner scriptRunner = new ScriptRunner();

        final Context context = new Context();
        context.setTransaction(transaction);

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        for (int i=0; i<transactionInputs.getSize(); ++i) {
            final TransactionInput transactionInput = transactionInputs.get(i);
            final BlockValidator.TransactionOutputIdentifier unspentOutputToSpendIdentifier = new BlockValidator.TransactionOutputIdentifier(transactionInput.getPreviousOutputTransactionHash(), transactionInput.getPreviousOutputIndex());
            final TransactionOutput outputToSpend = _findTransactionOutput(unspentOutputToSpendIdentifier);
            if (outputToSpend == null) { return false; }

            final Script lockingScript = outputToSpend.getLockingScript();
            final Script unlockingScript = transactionInput.getUnlockingScript();

            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(outputToSpend);
            context.setTransactionInputIndex(i);

            final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);
            if (! inputIsUnlocked) {
                final TransactionDeflater transactionDeflater = new TransactionDeflater();
                Logger.log("Transaction failed to verify: "+ BitcoinUtil.toHexString(transactionDeflater.toBytes(transaction)));
                return false;
            }
        }

        return true;
    }
}
