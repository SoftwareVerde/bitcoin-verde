package com.softwareverde.bitcoin.transaction.signer;

import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.constable.map.mutable.MutableMap;

public class HashMapTransactionOutputRepository implements TransactionOutputRepository {
    protected final MutableMap<TransactionOutputIdentifier, TransactionOutput> _data = new MutableHashMap<>();

    @Override
    public TransactionOutput get(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return _data.get(transactionOutputIdentifier);
    }

    public void put(final TransactionOutputIdentifier transactionOutputIdentifier, final TransactionOutput transactionOutput) {
        _data.put(transactionOutputIdentifier, transactionOutput.asConst());
    }
}
