package com.softwareverde.bitcoin.transaction.signer;

import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.Mode;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;

public class SignatureContextGenerator {
    // Reference: https://en.bitcoin.it/wiki/OP_CHECKSIG

    private final TransactionDatabaseManager _transactionDatabaseManager;
    private final TransactionOutputDatabaseManager _transactionOutputDatabaseManager;

    public SignatureContextGenerator(final MysqlDatabaseConnection databaseConnection) {
        _transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection);
        _transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(databaseConnection);
    }

    public SignatureContextGenerator(final TransactionDatabaseManager transactionDatabaseManager, final TransactionOutputDatabaseManager transactionOutputDatabaseManager) {
        _transactionDatabaseManager = transactionDatabaseManager;
        _transactionOutputDatabaseManager = transactionOutputDatabaseManager;
    }

    public SignatureContext createContextForEntireTransaction(final BlockChainSegmentId blockChainSegmentId, final Transaction transaction) throws DatabaseException {
        final SignatureContext signatureContext = new SignatureContext(transaction, new HashType(Mode.SIGNATURE_HASH_ALL, true), Long.MAX_VALUE);

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        for (int i=0; i<transactionInputs.getSize(); ++i) {
            final TransactionInput transactionInput = transactionInputs.get(i);

            final TransactionId transactionId = _transactionDatabaseManager.getTransactionIdFromHash(blockChainSegmentId, transactionInput.getPreviousOutputTransactionHash());
            final TransactionOutputId transactionOutputId = _transactionOutputDatabaseManager.findTransactionOutput(transactionId, transactionInput.getPreviousOutputIndex());
            final TransactionOutput transactionOutput = _transactionOutputDatabaseManager.getTransactionOutput(transactionOutputId);

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
