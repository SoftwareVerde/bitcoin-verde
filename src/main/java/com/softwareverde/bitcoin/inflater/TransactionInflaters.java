package com.softwareverde.bitcoin.inflater;

import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;

public interface TransactionInflaters extends Inflater {
    TransactionInflater getTransactionInflater();
    TransactionDeflater getTransactionDeflater();
}
