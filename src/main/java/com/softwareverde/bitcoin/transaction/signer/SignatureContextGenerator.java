package com.softwareverde.bitcoin.transaction.signer;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.Mode;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;

import java.util.HashMap;

public class SignatureContextGenerator {
    // Reference: https://en.bitcoin.it/wiki/OP_CHECKSIG

    public SignatureContextGenerator() { }

    public SignatureContext createContextForEntireTransaction(final Transaction transaction, final HashMap<TransactionOutputIdentifier, TransactionOutput> transactionOutputsToSpend, final Boolean useBitcoinCash) {
        final SignatureContext signatureContext = new SignatureContext(transaction, new HashType(Mode.SIGNATURE_HASH_ALL, true, useBitcoinCash), Long.MAX_VALUE);

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        for (int i=0; i<transactionInputs.getSize(); ++i) {
            final TransactionInput transactionInput = transactionInputs.get(i);

            final TransactionOutput transactionOutput = transactionOutputsToSpend.get(TransactionOutputIdentifier.fromTransactionInput(transactionInput));

            signatureContext.setShouldSignInputScript(i, true, transactionOutput);
        }

        return signatureContext;
    }

    public SignatureContext createContextForSingleOutputAndAllInputs(final Transaction transaction, final Integer outputIndex) throws DatabaseException {
        return null; // TODO...
    }

    public SignatureContext createContextForAllInputsAndNoOutputs(final Transaction transaction) throws DatabaseException {
        return null; // TODO...
    }
}
