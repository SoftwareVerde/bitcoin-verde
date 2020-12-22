package com.softwareverde.bitcoin.block.validator.thread;

import com.softwareverde.bitcoin.constable.util.ConstUtil;
import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.validator.BlockOutputs;
import com.softwareverde.bitcoin.transaction.validator.SpentOutputsTracker;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;

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

    protected final UnspentTransactionOutputContext _unspentTransactionOutputSet;
    protected final BlockOutputs _blockOutputs;
    protected final SpentOutputsTracker _spentOutputsTracker;

    protected final MutableList<Transaction> _invalidTransactions = new MutableList<Transaction>(0);

    protected TransactionOutput _getUnspentTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        { // Ensure the output hasn't been spent already by this block...
            final Boolean wasAlreadySpent = _spentOutputsTracker.markOutputAsSpent(transactionOutputIdentifier);
            if (wasAlreadySpent) {
                Logger.debug("Output already spent within Block: " + transactionOutputIdentifier);
                return null;
            }
        }

        final UnspentTransactionOutputContext unspentTransactionOutputSet = _unspentTransactionOutputSet;
        if (unspentTransactionOutputSet != null) {
            final TransactionOutput transactionOutput = unspentTransactionOutputSet.getTransactionOutput(transactionOutputIdentifier);
            if (transactionOutput != null) { return transactionOutput; }
        }

        final BlockOutputs blockOutputs = _blockOutputs;
        if (blockOutputs != null) {
            final TransactionOutput transactionOutput = blockOutputs.getTransactionOutput(transactionOutputIdentifier);
            if (transactionOutput != null) { return transactionOutput; }
        }

        return null;
    }

    protected Long _calculateTotalTransactionInputs(final Transaction transaction) {
        long totalInputValue = 0L;
        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();

        for (int i = 0; i < transactionInputs.getCount(); ++i) {
            final TransactionInput transactionInput = transactionInputs.get(i);

            final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
            final TransactionOutput transactionOutput = _getUnspentTransactionOutput(transactionOutputIdentifier);

            if (transactionOutput == null) {
                final Sha256Hash transactionHash = transaction.getHash();
                Logger.debug("Tx " + transactionHash + ":" + i + ", Output Not Found: " + transactionOutputIdentifier);
                return -1L;
            }

            totalInputValue += transactionOutput.getAmount();
        }

        return totalInputValue;
    }

    protected Long _totalFees = 0L;

    public TotalExpenditureTaskHandler(final UnspentTransactionOutputContext unspentTransactionOutputSet, final BlockOutputs blockOutputs, final SpentOutputsTracker spentOutputsTracker) {
        _unspentTransactionOutputSet = unspentTransactionOutputSet;
        _blockOutputs = blockOutputs;
        _spentOutputsTracker = spentOutputsTracker;
    }

    @Override
    public void init() { }

    @Override
    public void executeTask(final Transaction transaction) {
        if (! _invalidTransactions.isEmpty()) { return; }

        final Long totalOutputValue = transaction.getTotalOutputValue();
        final Long totalInputValue = _calculateTotalTransactionInputs(transaction);

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
