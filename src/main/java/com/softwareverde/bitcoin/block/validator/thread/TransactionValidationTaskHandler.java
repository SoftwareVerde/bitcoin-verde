package com.softwareverde.bitcoin.block.validator.thread;

import com.softwareverde.bitcoin.constable.util.ConstUtil;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidationResult;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;

import java.util.concurrent.atomic.AtomicInteger;

public class TransactionValidationTaskHandler implements TaskHandler<Transaction, TransactionValidationTaskHandler.TransactionValidationTaskResult> {
    public static class TransactionValidationTaskResult {
        public static TransactionValidationTaskResult invalid(final Transaction invalidTransaction) {
            final ImmutableListBuilder<Sha256Hash> invalidTransactions = new ImmutableListBuilder<Sha256Hash>(1);
            invalidTransactions.add(invalidTransaction.getHash());
            return new TransactionValidationTaskResult(false, invalidTransactions.build(), null);
        }

        public static TransactionValidationTaskResult invalid(final List<Transaction> invalidTransactions) {
            final ImmutableListBuilder<Sha256Hash> invalidTransactionHashes = new ImmutableListBuilder<Sha256Hash>(1);
            for (final Transaction transaction : invalidTransactions) {
                invalidTransactionHashes.add(transaction.getHash());
            }
            return new TransactionValidationTaskResult(false, invalidTransactionHashes.build(), null);
        }

        public static TransactionValidationTaskResult valid(final Integer signatureOperationCount) {
            return new TransactionValidationTaskResult(true, null, signatureOperationCount);
        }

        public final Boolean isValid;
        public final List<Sha256Hash> invalidTransactions;
        public final Integer signatureOperationCount;

        public TransactionValidationTaskResult(final Boolean isValid, final List<Sha256Hash> invalidTransactions, final Integer signatureOperationCount) {
            this.isValid = isValid;
            this.invalidTransactions = ConstUtil.asConstOrNull(invalidTransactions);
            this.signatureOperationCount = signatureOperationCount;
        }
    }

    protected final Long _blockHeight;
    protected final MutableList<Transaction> _invalidTransactions = new MutableList<Transaction>(0);
    protected final AtomicInteger _signatureOperationCount = new AtomicInteger(0);

    protected final TransactionValidator _transactionValidator;

    public TransactionValidationTaskHandler(final Long blockHeight, final TransactionValidator transactionValidator) {
        _blockHeight = blockHeight;
        _transactionValidator = transactionValidator;
    }

    @Override
    public void init() { }

    @Override
    public void executeTask(final Transaction transaction) {
        if (! _invalidTransactions.isEmpty()) { return; }

        final TransactionValidationResult transactionValidationResult;
        {
            TransactionValidationResult validationResult;
            try {
                validationResult = _transactionValidator.validateTransaction(_blockHeight, transaction);
            }
            catch (final Exception exception) {
                validationResult = TransactionValidationResult.invalid("An internal error occurred.");
                Logger.debug(exception);
            }
            transactionValidationResult = validationResult;
        }

        if (transactionValidationResult.isValid) {
            final Integer signatureOperationCount = transactionValidationResult.getSignatureOperationCount();
            _signatureOperationCount.addAndGet(signatureOperationCount);
        }
        else {
            _invalidTransactions.add(transaction);
        }
    }

    @Override
    public TransactionValidationTaskResult getResult() {
        if (! _invalidTransactions.isEmpty()) {
            return TransactionValidationTaskResult.invalid(_invalidTransactions);
        }

        final Integer signatureOperationCount = _signatureOperationCount.get();
        return TransactionValidationTaskResult.valid(signatureOperationCount);
    }
}
