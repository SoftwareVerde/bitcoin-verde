package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.server.module.node.database.indexer.TransactionOutputId;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public interface AtomicTransactionOutputIndexerContext extends AutoCloseable {
    void startDatabaseTransaction() throws ContextException;
    void commitDatabaseTransaction() throws ContextException;
    void rollbackDatabaseTransaction();

    List<TransactionId> getUnprocessedTransactions(Integer batchSize) throws ContextException;
    void dequeueTransactionsForProcessing(List<TransactionId> transactionIds) throws ContextException;

    TransactionId getTransactionId(Sha256Hash transactionHash) throws ContextException;
    TransactionId getTransactionId(SlpTokenId slpTokenId) throws ContextException;
    Transaction getTransaction(TransactionId transactionId) throws ContextException;

    void indexTransactionOutput(TransactionId transactionId, Integer outputIndex, Long amount, ScriptType scriptType, Address address, TransactionId slpTransactionId, ByteArray memoActionType, ByteArray memoActionIdentifier) throws ContextException;
    void indexTransactionInput(TransactionId transactionId, Integer inputIndex, TransactionOutputId transactionOutputId) throws ContextException;

    @Override
    void close() throws ContextException;
}
