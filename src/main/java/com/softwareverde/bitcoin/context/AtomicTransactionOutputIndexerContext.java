package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.server.module.node.database.indexer.TransactionOutputId;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.map.Map;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;



public interface AtomicTransactionOutputIndexerContext extends AutoCloseable {
    void initialize() throws ContextException;
    TransactionId finish() throws ContextException;

    List<TransactionId> getUnprocessedTransactions(Integer batchSize) throws ContextException;
    void markTransactionProcessed(TransactionId transactionId) throws ContextException;

    void storeTransactions(List<Sha256Hash> transactionHashes, List<Integer> byteCounts) throws ContextException;
    TransactionId getTransactionId(Sha256Hash transactionHash) throws ContextException;
    TransactionId getTransactionId(SlpTokenId slpTokenId) throws ContextException;
    Map<Sha256Hash, TransactionId> getTransactionIds(List<Sha256Hash> transactionHashes) throws ContextException;
    Transaction getTransaction(TransactionId transactionId) throws ContextException;

    void indexTransactionOutput(TransactionId transactionId, Integer outputIndex, Long amount, ScriptType scriptType, Address address, Sha256Hash scriptHash, TransactionId slpTransactionId, ByteArray memoActionType, ByteArray memoActionIdentifier) throws ContextException;
    void indexTransactionInput(TransactionId transactionId, Integer inputIndex, TransactionOutputId transactionOutputId) throws ContextException;

    @Override
    void close() throws ContextException;
}
