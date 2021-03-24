package com.softwareverde.bitcoin.transaction.dsproof;

import com.softwareverde.bitcoin.server.message.type.dsproof.DoubleSpendProofPreimage;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;

public class DoubleSpendProofWithTransactions extends DoubleSpendProof {
    protected final Transaction _firstSeenTransaction;
    protected final Transaction _doubleSpendTransaction;

    public DoubleSpendProofWithTransactions(final TransactionOutputIdentifier transactionOutputIdentifier, final DoubleSpendProofPreimage doubleSpendProofPreimage0, final DoubleSpendProofPreimage doubleSpendProofPreimage1, final Transaction firstSeenTransaction, final Transaction doubleSpendTransaction) {
        super(transactionOutputIdentifier, doubleSpendProofPreimage0, doubleSpendProofPreimage1);

        _firstSeenTransaction = firstSeenTransaction;
        _doubleSpendTransaction = doubleSpendTransaction;
    }

    public Transaction getFirstSeenTransaction() {
        return _firstSeenTransaction;
    }

    public Transaction getDoubleSpendTransaction() {
        return _doubleSpendTransaction;
    }
}
