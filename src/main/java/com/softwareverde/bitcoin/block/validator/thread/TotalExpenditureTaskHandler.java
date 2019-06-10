package com.softwareverde.bitcoin.block.validator.thread;

import com.softwareverde.bitcoin.constable.util.ConstUtil;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.module.node.database.core.CoreDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.output.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;

import java.util.Map;

/**
 * Calculates the total fees available for all Transactions sent to executeTask.
 * If any expenditures are invalid (i.e. inputs < outputs), then ExpenditureResult.isValid will be false.
 */
public class TotalExpenditureTaskHandler implements TaskHandler<Transaction, TotalExpenditureTaskHandler.ExpenditureResult> {
    public static class ExpenditureResult {
        public static ExpenditureResult invalid(final Transaction invalidTransaction) {
            final ImmutableListBuilder<Sha256Hash> invalidTransactions = new ImmutableListBuilder<Sha256Hash>(1);
            invalidTransactions.add(invalidTransaction.getHash());
            return new ExpenditureResult(false, null, invalidTransactions.build());
        }

        public static ExpenditureResult invalid(final List<Transaction> invalidTransactions) {
            final ImmutableListBuilder<Sha256Hash> invalidTransactionHashes = new ImmutableListBuilder<Sha256Hash>(1);
            for (final Transaction transaction : invalidTransactions) {
                invalidTransactionHashes.add(transaction.getHash());
            }
            return new ExpenditureResult(false, null, invalidTransactionHashes.build());
        }

        public static ExpenditureResult valid(final Long totalFees) {
            return new ExpenditureResult(true, totalFees, null);
        }

        public final Boolean isValid;
        public final Long totalFees;
        public final List<Sha256Hash> invalidTransactions;

        public ExpenditureResult(final Boolean isValid, final Long totalFees, final List<Sha256Hash> invalidTransactions) {
            this.isValid = isValid;
            this.totalFees = totalFees;
            this.invalidTransactions = ConstUtil.asConstOrNull(invalidTransactions);
        }
    }

    protected CoreDatabaseManager _databaseManager;
    protected final MutableList<Transaction> _invalidTransactions = new MutableList<Transaction>(0);

    protected static TransactionOutput _getTransactionOutput(final CoreDatabaseManager databaseManager, final Sha256Hash outputTransactionHash, final Integer transactionOutputIndex, final Map<Sha256Hash, Transaction> queuedTransactions) {
        try {
            final TransactionOutputDatabaseManager transactionOutputDatabaseManager = databaseManager.getTransactionOutputDatabaseManager();

            final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(outputTransactionHash, transactionOutputIndex);
            final TransactionOutputId transactionOutputId = transactionOutputDatabaseManager.findTransactionOutput(transactionOutputIdentifier);
            if (transactionOutputId != null) {
                return transactionOutputDatabaseManager.getTransactionOutput(transactionOutputId);
            }
            else {
                final Transaction transactionContainingOutput = queuedTransactions.get(outputTransactionHash);
                if (transactionContainingOutput != null) {
                    final List<TransactionOutput> transactionOutputs = transactionContainingOutput.getTransactionOutputs();
                    final boolean transactionOutputIndexIsValid = (transactionOutputIndex < transactionOutputs.getSize());
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

    protected static Long _calculateTotalTransactionInputs(final CoreDatabaseManager databaseManager, final Transaction transaction, final Map<Sha256Hash, Transaction> queuedTransactions) {
        long totalInputValue = 0L;
        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();

        for (int i=0; i<transactionInputs.getSize(); ++i) {
            final TransactionInput transactionInput = transactionInputs.get(i);

            final Sha256Hash outputTransactionHash = transactionInput.getPreviousOutputTransactionHash();
            final Integer transactionOutputIndex = transactionInput.getPreviousOutputIndex();

            final TransactionOutput transactionOutput = _getTransactionOutput(databaseManager, outputTransactionHash, transactionOutputIndex, queuedTransactions);

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
    public void init(final CoreDatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    @Override
    public void executeTask(final Transaction transaction) {
        if (! _invalidTransactions.isEmpty()) { return; }

        final Long totalOutputValue = transaction.getTotalOutputValue();
        final Long totalInputValue = _calculateTotalTransactionInputs(_databaseManager, transaction, _queuedTransactionOutputs);

        final boolean transactionExpenditureIsValid = (totalOutputValue <= totalInputValue);
        if (! transactionExpenditureIsValid) {
            _invalidTransactions.add(transaction);
            return;
        }

        _totalFees += (totalInputValue - totalOutputValue);
    }

    @Override
    public ExpenditureResult getResult() {
        if (! _invalidTransactions.isEmpty()) {
            return ExpenditureResult.invalid(_invalidTransactions);
        }

        return ExpenditureResult.valid(_totalFees);
    }
}
