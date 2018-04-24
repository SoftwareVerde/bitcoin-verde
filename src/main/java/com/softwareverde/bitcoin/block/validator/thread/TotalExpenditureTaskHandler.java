package com.softwareverde.bitcoin.block.validator.thread;

import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
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
    protected boolean _allTransactionsExpendituresAreValid = true;

    protected static TransactionOutput _findTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier, final TransactionDatabaseManager transactionDatabaseManager, final TransactionOutputDatabaseManager transactionOutputDatabaseManager) {
        try {
            final Integer transactionOutputIndex = transactionOutputIdentifier.getOutputIndex();
            final TransactionId transactionId = transactionDatabaseManager.getTransactionIdFromHash(transactionOutputIdentifier.getBlockChainSegmentId(), transactionOutputIdentifier.getTransactionHash());
            if (transactionId == null) { return null; }

            final TransactionOutputId transactionOutputId = transactionOutputDatabaseManager.findTransactionOutput(transactionId, transactionOutputIndex);
            if (transactionOutputId == null) { return null; }

            return transactionOutputDatabaseManager.getTransactionOutput(transactionOutputId);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    protected static Long _calculateTotalTransactionInputs(final BlockChainSegmentId blockChainSegmentId, final Transaction transaction, final Map<Sha256Hash, Transaction> queuedTransactions, final MysqlDatabaseConnection databaseConnection) {
        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection);
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(databaseConnection);

        long totalInputValue = 0L;
        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();

        for (int i=0; i<transactionInputs.getSize(); ++i) {
            final TransactionInput transactionInput = transactionInputs.get(i);

            final Sha256Hash outputTransactionHash = transactionInput.getPreviousOutputTransactionHash();
            final Integer transactionOutputIndex = transactionInput.getPreviousOutputIndex();

            final TransactionOutput transactionOutput;
            {
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(blockChainSegmentId, outputTransactionHash, transactionOutputIndex);
                TransactionOutput possibleTransactionOutput = _findTransactionOutput(transactionOutputIdentifier, transactionDatabaseManager, transactionOutputDatabaseManager);
                if (possibleTransactionOutput == null) {
                    final Transaction transactionContainingOutput = queuedTransactions.get(outputTransactionHash);
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
                Logger.log("Tx Input, Output Not Found: " + HexUtil.toHexString(outputTransactionHash.getBytes()) + ":" + transactionOutputIndex);
                totalInputValue = -1L;
                break;
            }

            totalInputValue += transactionOutput.getAmount();
        }

        return totalInputValue;
    }

    private final BlockChainSegmentId _blockChainSegmentId;
    private final Map<Sha256Hash, Transaction> _queuedTransactionOutputs;
    private Long _totalFees = 0L;

    public TotalExpenditureTaskHandler(final BlockChainSegmentId blockChainSegmentId, final Map<Sha256Hash, Transaction> queuedTransactionOutputs) {
        _blockChainSegmentId = blockChainSegmentId;
        _queuedTransactionOutputs = queuedTransactionOutputs;
    }

    @Override
    public void init(final MysqlDatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    @Override
    public void executeTask(final Transaction transaction) {
        if (! _allTransactionsExpendituresAreValid) { return; }

        final Long totalOutputValue = transaction.getTotalOutputValue();
        final Long totalInputValue = _calculateTotalTransactionInputs(_blockChainSegmentId, transaction, _queuedTransactionOutputs, _databaseConnection);

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
