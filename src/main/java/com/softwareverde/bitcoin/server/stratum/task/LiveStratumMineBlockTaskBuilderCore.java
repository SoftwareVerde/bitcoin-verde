package com.softwareverde.bitcoin.server.stratum.task;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionWithFee;
import com.softwareverde.bitcoin.transaction.coinbase.CoinbaseTransaction;
import com.softwareverde.bitcoin.transaction.coinbase.MutableCoinbaseTransaction;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;

import java.util.concurrent.ConcurrentHashMap;

public class LiveStratumMineBlockTaskBuilderCore extends StratumMineBlockTaskBuilderCore {
    protected final ConcurrentHashMap<Sha256Hash, TransactionWithFee> _transactionsWithFee = new ConcurrentHashMap<>();

    public LiveStratumMineBlockTaskBuilderCore(final Integer totalExtraNonceByteCount, final TransactionDeflater transactionDeflater) {
        super(totalExtraNonceByteCount, transactionDeflater);
    }

    public void addTransaction(final TransactionWithFee transactionWithFee) {
        try {
            _prototypeBlockWriteLock.lock();

            final Transaction transaction = transactionWithFee.transaction;
            final Long transactionFee = transactionWithFee.transactionFee;

            _prototypeBlock.addTransaction(transaction);
            _transactionsWithFee.put(transaction.getHash(), transactionWithFee);

            final CoinbaseTransaction coinbaseTransaction = _prototypeBlock.getCoinbaseTransaction();
            final MutableCoinbaseTransaction mutableCoinbaseTransaction = new MutableCoinbaseTransaction(coinbaseTransaction);
            final Long currentBlockReward = coinbaseTransaction.getBlockReward();
            mutableCoinbaseTransaction.setBlockReward(currentBlockReward + transactionFee);

            _setCoinbaseTransaction(mutableCoinbaseTransaction);
        }
        finally {
            _prototypeBlockWriteLock.unlock();
        }
    }

    public void removeTransaction(final Sha256Hash transactionHash) {
        try {
            _prototypeBlockWriteLock.lock();

            _prototypeBlock.removeTransaction(transactionHash);

            final TransactionWithFee transactionWithFee = _transactionsWithFee.get(transactionHash);
            if (transactionWithFee == null) {
                Logger.warn("Unable to remove transaction from prototype block: " + transactionHash);
                return;
            }

            final Long transactionFee = transactionWithFee.transactionFee;

            final CoinbaseTransaction coinbaseTransaction = _prototypeBlock.getCoinbaseTransaction();
            final MutableCoinbaseTransaction mutableCoinbaseTransaction = new MutableCoinbaseTransaction(coinbaseTransaction);
            final Long currentBlockReward = coinbaseTransaction.getBlockReward();
            mutableCoinbaseTransaction.setBlockReward(currentBlockReward - transactionFee);

            _setCoinbaseTransaction(mutableCoinbaseTransaction);

        }
        finally {
            _prototypeBlockWriteLock.unlock();
        }
    }

    public void clearTransactions() {
        try {
            _prototypeBlockWriteLock.lock();

            final Transaction coinbaseTransaction = _prototypeBlock.getCoinbaseTransaction();
            _prototypeBlock.clearTransactions();
            _prototypeBlock.addTransaction(coinbaseTransaction);
        }
        finally {
            _prototypeBlockWriteLock.unlock();
        }
    }
}
