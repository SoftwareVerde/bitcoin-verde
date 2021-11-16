package com.softwareverde.bitcoin.transaction.dsproof;

import com.softwareverde.bitcoin.server.message.type.dsproof.DoubleSpendProofPreimage;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptType;

public class DoubleSpendProofWithTransactions extends DoubleSpendProof {
    protected final Transaction _transaction0;
    protected final Transaction _transaction1;

    protected DoubleSpendProofWithTransactions(final TransactionOutputIdentifier transactionOutputIdentifier, final DoubleSpendProofPreimage doubleSpendProofPreimage0, final DoubleSpendProofPreimage doubleSpendProofPreimage1, final Transaction transaction0, final Transaction transaction1) {
        super(transactionOutputIdentifier, doubleSpendProofPreimage0, doubleSpendProofPreimage1);

        _transaction0 = transaction0;
        _transaction1 = transaction1;
    }

    public static DoubleSpendProofWithTransactions create(final Transaction firstSeenTransaction, final Transaction doubleSpendTransaction, final TransactionOutputIdentifier transactionOutputIdentifierBeingSpent, final ScriptType previousOutputScriptType) {
        final DoubleSpendProofPreimage firstSeenDoubleSpendProofPreimage = DoubleSpendProof.createDoubleSpendProofPreimage(firstSeenTransaction, transactionOutputIdentifierBeingSpent, previousOutputScriptType);
        final DoubleSpendProofPreimage secondSeenDoubleSpendProofPreimage = DoubleSpendProof.createDoubleSpendProofPreimage(doubleSpendTransaction, transactionOutputIdentifierBeingSpent, previousOutputScriptType);

        if (firstSeenDoubleSpendProofPreimage == null || secondSeenDoubleSpendProofPreimage == null) {
            return null;
        }

        final boolean preimagesAreInCanonicalOrder = DoubleSpendProof.arePreimagesInCanonicalOrder(firstSeenDoubleSpendProofPreimage, secondSeenDoubleSpendProofPreimage);

        final DoubleSpendProofPreimage doubleSpendProofPreimage0;
        final DoubleSpendProofPreimage doubleSpendProofPreimage1;
        final Transaction transaction0;
        final Transaction transaction1;
        if (preimagesAreInCanonicalOrder) {
            doubleSpendProofPreimage0 = firstSeenDoubleSpendProofPreimage;
            doubleSpendProofPreimage1 = secondSeenDoubleSpendProofPreimage;
            transaction0 = firstSeenTransaction;
            transaction1 = doubleSpendTransaction;
        }
        else {
            doubleSpendProofPreimage0 = secondSeenDoubleSpendProofPreimage;
            doubleSpendProofPreimage1 = firstSeenDoubleSpendProofPreimage;
            transaction0 = doubleSpendTransaction;
            transaction1 = firstSeenTransaction;
        }

        return new DoubleSpendProofWithTransactions(transactionOutputIdentifierBeingSpent, doubleSpendProofPreimage0, doubleSpendProofPreimage1, transaction0, transaction1);
    }

    public Transaction getTransaction0() {
        return _transaction0;
    }

    public Transaction getTransaction1() {
        return _transaction1;
    }
}
