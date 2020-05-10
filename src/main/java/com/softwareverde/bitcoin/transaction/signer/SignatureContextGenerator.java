package com.softwareverde.bitcoin.transaction.signer;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.Mode;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

public class SignatureContextGenerator {
    // Reference: https://en.bitcoin.it/wiki/OP_CHECKSIG

    protected final TransactionOutputRepository _transactionOutputRepository;

    public SignatureContextGenerator(final TransactionOutputRepository transactionOutputRepository) {
        _transactionOutputRepository = transactionOutputRepository;
    }

    public SignatureContext createContextForEntireTransaction(final Transaction transaction, final Boolean useBitcoinCash) {
        final SignatureContext signatureContext = new SignatureContext(transaction, new HashType(Mode.SIGNATURE_HASH_ALL, true, useBitcoinCash), Long.MAX_VALUE);

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        for (int i = 0; i < transactionInputs.getCount(); ++i) {
            final TransactionInput transactionInput = transactionInputs.get(i);

            final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
            final TransactionOutput transactionOutput = _transactionOutputRepository.get(transactionOutputIdentifier);
            if (transactionOutput == null) {
                Logger.debug("Unable to create SignatureContext for Transaction.  Unknown TransactionOutput: " + transactionOutputIdentifier);
                return null;
            }

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
