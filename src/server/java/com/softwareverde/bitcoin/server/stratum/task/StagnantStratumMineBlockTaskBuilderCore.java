package com.softwareverde.bitcoin.server.stratum.task;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.constable.list.List;

public class StagnantStratumMineBlockTaskBuilderCore extends StratumMineBlockTaskBuilderCore {
    public StagnantStratumMineBlockTaskBuilderCore(final Integer totalExtraNonceByteCount, final TransactionDeflater transactionDeflater) {
        super(totalExtraNonceByteCount, transactionDeflater);
    }

    public void addTransactions(final List<Transaction> transactions) {
        try {
            _prototypeBlockWriteLock.lock();

            for (final Transaction transaction : transactions) {
                _prototypeBlock.addTransaction(transaction);
            }
        }
        finally {
            _prototypeBlockWriteLock.unlock();
        }
    }

    public void clearTransaction() {
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
