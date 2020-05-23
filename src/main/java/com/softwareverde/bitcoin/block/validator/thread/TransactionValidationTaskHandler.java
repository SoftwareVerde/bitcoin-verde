package com.softwareverde.bitcoin.block.validator.thread;

import com.softwareverde.bitcoin.constable.util.*;
import com.softwareverde.bitcoin.context.*;
import com.softwareverde.bitcoin.transaction.*;
import com.softwareverde.bitcoin.transaction.validator.*;
import com.softwareverde.constable.list.*;
import com.softwareverde.constable.list.immutable.*;
import com.softwareverde.constable.list.mutable.*;
import com.softwareverde.logging.*;
import com.softwareverde.security.hash.sha256.*;

public class TransactionValidationTaskHandler<Context extends NetworkTimeContext & MedianBlockTimeContext & UnspentTransactionOutputContext> implements TaskHandler<Transaction, TransactionValidationTaskHandler.TransactionValidationResult> {
    public static class TransactionValidationResult {
        public static TransactionValidationResult invalid(final Transaction invalidTransaction) {
            final ImmutableListBuilder<Sha256Hash> invalidTransactions = new ImmutableListBuilder<Sha256Hash>(1);
            invalidTransactions.add(invalidTransaction.getHash());
            return new TransactionValidationResult(false, invalidTransactions.build());
        }

        public static TransactionValidationResult invalid(final List<Transaction> invalidTransactions) {
            final ImmutableListBuilder<Sha256Hash> invalidTransactionHashes = new ImmutableListBuilder<Sha256Hash>(1);
            for (final Transaction transaction : invalidTransactions) {
                invalidTransactionHashes.add(transaction.getHash());
            }
            return new TransactionValidationResult(false, invalidTransactionHashes.build());
        }

        public static TransactionValidationResult valid() {
            return new TransactionValidationResult(true, null);
        }

        public final Boolean isValid;
        public final List<Sha256Hash> invalidTransactions;

        public TransactionValidationResult(final Boolean isValid, final List<Sha256Hash> invalidTransactions) {
            this.isValid = isValid;
            this.invalidTransactions = ConstUtil.asConstOrNull(invalidTransactions);
        }
    }

    protected final Long _blockHeight;
    protected final Context _context;
    protected final BlockOutputs _blockOutputs;
    protected final MutableList<Transaction> _invalidTransactions = new MutableList<Transaction>(0);

    protected TransactionValidator _transactionValidator;

    public TransactionValidationTaskHandler(final Context context, final Long blockHeight, final BlockOutputs blockOutputs) {
        _context = context;
        _blockHeight = blockHeight;
        _blockOutputs = blockOutputs;
    }

    @Override
    public void init() {
        _transactionValidator = new TransactionValidatorCore<Context>(_blockOutputs, _context);
    }

    @Override
    public void executeTask(final Transaction transaction) {
        if (! _invalidTransactions.isEmpty()) { return; }

        final Boolean transactionInputsAreUnlocked;
        {
            Boolean inputsAreUnlocked = false;
            try {
                inputsAreUnlocked = _transactionValidator.validateTransaction(_blockHeight, transaction, false);
            }
            catch (final Exception exception) { Logger.warn(exception); }
            transactionInputsAreUnlocked = inputsAreUnlocked;
        }

        if (! transactionInputsAreUnlocked) {
            _invalidTransactions.add(transaction);
        }
    }

    @Override
    public TransactionValidationResult getResult() {
        if (! _invalidTransactions.isEmpty()) {
            return TransactionValidationResult.invalid(_invalidTransactions);
        }

        return TransactionValidationResult.valid();
    }
}
