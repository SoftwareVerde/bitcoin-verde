package com.softwareverde.bitcoin.block.validator.thread;

import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.constable.util.ConstUtil;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;
import com.softwareverde.network.time.NetworkTime;

public class TransactionValidationTaskHandler implements TaskHandler<Transaction, TransactionValidationTaskHandler.TransactionValidationResult> {
    public static class TransactionValidationResult {
        public static TransactionValidationResult invalid(final Transaction invalidTransaction) {
            final ImmutableListBuilder<Sha256Hash> invalidTransactions = new ImmutableListBuilder<>(1);
            invalidTransactions.add(invalidTransaction.getHash());
            return new TransactionValidationResult(false, invalidTransactions.build());
        }

        public static TransactionValidationResult invalid(final List<Transaction> invalidTransactions) {
            final ImmutableListBuilder<Sha256Hash> invalidTransactionHashes = new ImmutableListBuilder<>(1);
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

    protected final BlockchainSegmentId _blockchainSegmentId;
    protected final Long _blockHeight;
    protected final NetworkTime _networkTime;
    protected final MedianBlockTime _medianBlockTime;
    protected TransactionValidator _transactionValidator;
    protected final MutableList<Transaction> _invalidTransactions = new MutableList<>(0);

    public TransactionValidationTaskHandler(final BlockchainSegmentId blockchainSegmentId, final Long blockHeight, final NetworkTime networkTime, final MedianBlockTime medianBlockTime) {
        _blockchainSegmentId = blockchainSegmentId;
        _blockHeight = blockHeight;
        _networkTime = networkTime.asConst(); // NOTE: This freezes the networkTime...
        _medianBlockTime = medianBlockTime.asConst(); // NOTE: This freezes the medianBlockTime... (but shouldn't matter)
    }

    @Override
    public void init(final DatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache) {
        _transactionValidator = new TransactionValidator(databaseConnection, databaseManagerCache, _networkTime, _medianBlockTime);
    }

    @Override
    public void executeTask(final Transaction transaction) {
        if (! _invalidTransactions.isEmpty()) { return; }

        final Boolean transactionInputsAreUnlocked;
        {
            Boolean inputsAreUnlocked = false;
            try {
                inputsAreUnlocked = _transactionValidator.validateTransaction(_blockchainSegmentId, _blockHeight, transaction, false);
            }
            catch (final Exception exception) { Logger.log(exception); }
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
