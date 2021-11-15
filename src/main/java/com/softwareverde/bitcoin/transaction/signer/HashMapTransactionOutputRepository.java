package com.softwareverde.bitcoin.transaction.signer;

import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;

import java.util.HashMap;

public class HashMapTransactionOutputRepository implements TransactionOutputRepository {
    protected final HashMap<TransactionOutputIdentifier, TransactionOutput> _data = new HashMap<>();

    @Override
    public TransactionOutput get(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return _data.get(transactionOutputIdentifier);
    }

    public void put(final TransactionOutputIdentifier transactionOutputIdentifier, final TransactionOutput transactionOutput) {
        _data.put(transactionOutputIdentifier, transactionOutput.asConst());
    }
}
