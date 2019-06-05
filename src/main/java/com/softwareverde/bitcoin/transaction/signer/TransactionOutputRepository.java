package com.softwareverde.bitcoin.transaction.signer;

import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;

public interface TransactionOutputRepository {
    TransactionOutput get(TransactionOutputIdentifier transactionOutputIdentifier);
}
