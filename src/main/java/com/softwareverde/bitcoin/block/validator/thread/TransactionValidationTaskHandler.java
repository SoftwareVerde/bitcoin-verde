package com.softwareverde.bitcoin.block.validator.thread;

import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.constable.util.ConstUtil;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorFactory;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.time.NetworkTime;

public class TransactionValidationTaskHandler implements TaskHandler<Transaction, TransactionValidationTaskHandler.TransactionValidationResult> {
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

    protected final BlockchainSegmentId _blockchainSegmentId;
    protected final Long _blockHeight;
    protected final NetworkTime _networkTime;
    protected final MedianBlockTime _medianBlockTime;
    protected final TransactionValidatorFactory _transactionValidatorFactory;
    protected final MutableList<Transaction> _invalidTransactions = new MutableList<Transaction>(0);

    protected TransactionValidator _transactionValidator;

    public TransactionValidationTaskHandler(final TransactionValidatorFactory transactionValidatorFactory, final BlockchainSegmentId blockchainSegmentId, final Long blockHeight, final NetworkTime networkTime, final MedianBlockTime medianBlockTime) {
        _blockchainSegmentId = blockchainSegmentId;
        _blockHeight = blockHeight;
        _networkTime = networkTime.asConst(); // NOTE: This freezes the networkTime...
        _medianBlockTime = medianBlockTime.asConst(); // NOTE: This freezes the medianBlockTime... (but shouldn't matter)
        _transactionValidatorFactory = transactionValidatorFactory;
    }

    @Override
    public void init(final FullNodeDatabaseManager databaseManager) {
        _transactionValidator = _transactionValidatorFactory.newTransactionValidator(databaseManager, _networkTime, _medianBlockTime);
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
