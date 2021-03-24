package com.softwareverde.bitcoin.block.validator.thread;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidationResult;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class TransactionValidationTaskHandler implements TaskHandler<Transaction, TransactionValidationTaskHandler.TransactionValidationTaskResult> {
    public static class TransactionValidationTaskResult {
        public static TransactionValidationTaskResult invalid(final Transaction invalidTransaction, final TransactionValidationResult transactionValidationResult) {
            final Map<Sha256Hash, TransactionValidationResult> invalidTransactions = new HashMap<Sha256Hash, TransactionValidationResult>(0);
            invalidTransactions.put(invalidTransaction.getHash(), transactionValidationResult);
            return new TransactionValidationTaskResult(false, invalidTransactions, null);
        }

        public static TransactionValidationTaskResult invalid(final Map<Transaction, TransactionValidationResult> invalidTransactions) {
            final Map<Sha256Hash, TransactionValidationResult> invalidTransactionsMap = new HashMap<Sha256Hash, TransactionValidationResult>(0);
            for (final Transaction transaction : invalidTransactions.keySet()) {
                final TransactionValidationResult transactionValidationResult = invalidTransactions.get(transaction);
                invalidTransactionsMap.put(transaction.getHash(), transactionValidationResult);
            }
            return new TransactionValidationTaskResult(false, invalidTransactionsMap, null);
        }

        public static TransactionValidationTaskResult valid(final Integer signatureOperationCount) {
            return new TransactionValidationTaskResult(true, null, signatureOperationCount);
        }

        protected final Boolean _isValid;
        protected final Map<Sha256Hash, TransactionValidationResult> _invalidTransactions;
        protected final Integer _signatureOperationCount;

        protected TransactionValidationTaskResult(final Boolean isValid, final Map<Sha256Hash, TransactionValidationResult> invalidTransactions, final Integer signatureOperationCount) {
            _isValid = isValid;
            _invalidTransactions = invalidTransactions;
            _signatureOperationCount = signatureOperationCount;
        }

        public Boolean isValid() {
            return _isValid;
        }

        public List<Sha256Hash> getInvalidTransactions() {
            return new ImmutableList<Sha256Hash>(_invalidTransactions.keySet());
        }

        public TransactionValidationResult getTransactionValidationResult(final Sha256Hash transactionHash) {
            return _invalidTransactions.get(transactionHash);
        }

        public Integer getSignatureOperationCount() {
            return _signatureOperationCount;
        }
    }

    protected final Long _blockHeight;
    protected final HashMap<Transaction, TransactionValidationResult> _invalidTransactions = new HashMap<Transaction, TransactionValidationResult>(0);
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
            final Integer signatureOperationCount = transactionValidationResult.signatureOperationCount;
            _signatureOperationCount.addAndGet(signatureOperationCount);
        }
        else {
            _invalidTransactions.put(transaction, transactionValidationResult);
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
