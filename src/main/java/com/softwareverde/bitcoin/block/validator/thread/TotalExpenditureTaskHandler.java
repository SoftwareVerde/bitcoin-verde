package com.softwareverde.bitcoin.block.validator.thread;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;

import java.util.Map;

/**
 * Calculates the total fees available for all Transactions sent to executeTask.
 * If any expenditures are invalid (i.e. inputs < outputs), then getResult will return null.
 */
public class TotalExpenditureTaskHandler implements TaskHandler<Transaction, Long> {
    protected MysqlDatabaseConnection _databaseConnection;
    protected DatabaseManagerCache _databaseManagerCache;
    protected boolean _allTransactionsExpendituresAreValid = true;

    protected static TransactionOutput _getTransactionOutput(final Sha256Hash outputTransactionHash, final Integer transactionOutputIndex, final Map<Sha256Hash, Transaction> queuedTransactions, final MysqlDatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache) {
        try {
            final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(databaseConnection, databaseManagerCache);

            final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(outputTransactionHash, transactionOutputIndex);
            final TransactionOutputId transactionOutputId = transactionOutputDatabaseManager.findTransactionOutput(transactionOutputIdentifier);
            if (transactionOutputId != null) {
                return transactionOutputDatabaseManager.getTransactionOutput(transactionOutputId);
            }
            else {
                final Transaction transactionContainingOutput = queuedTransactions.get(outputTransactionHash);
                if (transactionContainingOutput != null) {
                    final List<TransactionOutput> transactionOutputs = transactionContainingOutput.getTransactionOutputs();
                    final Boolean transactionOutputIndexIsValid = (transactionOutputIndex < transactionOutputs.getSize());
                    if (transactionOutputIndexIsValid) {
                        return transactionOutputs.get(transactionOutputIndex);
                    }
                }
            }
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }

        return null;
    }

    protected static Long _calculateTotalTransactionInputs(final Transaction transaction, final Map<Sha256Hash, Transaction> queuedTransactions, final MysqlDatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache) {
        long totalInputValue = 0L;
        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();

        for (int i=0; i<transactionInputs.getSize(); ++i) {
            final TransactionInput transactionInput = transactionInputs.get(i);

            final Sha256Hash outputTransactionHash = transactionInput.getPreviousOutputTransactionHash();
            final Integer transactionOutputIndex = transactionInput.getPreviousOutputIndex();

            final TransactionOutput transactionOutput = _getTransactionOutput(outputTransactionHash, transactionOutputIndex, queuedTransactions, databaseConnection, databaseManagerCache);

            if (transactionOutput == null) {
                Logger.log("Tx Input, Output Not Found: " + HexUtil.toHexString(outputTransactionHash.getBytes()) + ":" + transactionOutputIndex);
                return -1L;
            }

            totalInputValue += transactionOutput.getAmount();
        }

        return totalInputValue;
    }

    private final Map<Sha256Hash, Transaction> _queuedTransactionOutputs;
    private Long _totalFees = 0L;

    public TotalExpenditureTaskHandler(final Map<Sha256Hash, Transaction> queuedTransactionOutputs) {
        _queuedTransactionOutputs = queuedTransactionOutputs;
    }

    @Override
    public void init(final MysqlDatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache) {
        _databaseConnection = databaseConnection;
        _databaseManagerCache = databaseManagerCache;
    }

    @Override
    public void executeTask(final Transaction transaction) {
        if (! _allTransactionsExpendituresAreValid) { return; }

        final Long totalOutputValue = transaction.getTotalOutputValue();
        final Long totalInputValue = _calculateTotalTransactionInputs(transaction, _queuedTransactionOutputs, _databaseConnection, _databaseManagerCache);

        final boolean transactionExpenditureIsValid = (totalOutputValue <= totalInputValue);
        if (! transactionExpenditureIsValid) {
            _allTransactionsExpendituresAreValid = false;
        }

        _totalFees += (totalInputValue - totalOutputValue);
    }

    @Override
    public Long getResult() {
        if (! _allTransactionsExpendituresAreValid) { return null; }

        return _totalFees;
    }
}
