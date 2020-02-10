package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;

public interface UnspentTransactionOutputSet {
    TransactionOutput getUnspentTransactionOutput(TransactionOutputIdentifier transactionOutputIdentifier);
}
