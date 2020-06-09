package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.constable.list.List;

public interface AtomicTransactionOutputIndexerContext extends AutoCloseable {
    void commitDatabaseTransaction() throws ContextException;
    void rollbackDatabaseTransaction();

    AddressId getAddressId(Address address) throws ContextException;
    AddressId storeAddress(Address address) throws ContextException;

    List<TransactionId> getUnprocessedTransactions(Integer batchSize) throws ContextException;
    void dequeueTransactionsForProcessing(List<TransactionId> transactionIds) throws ContextException;

    TransactionId getTransactionId(SlpTokenId slpTokenId) throws ContextException;
    Transaction getTransaction(TransactionId transactionId) throws ContextException;

    void indexTransactionOutput(TransactionId transactionId, Integer outputIndex, Long amount, ScriptType scriptType, AddressId addressId, TransactionId slpTransactionId) throws ContextException;

    @Override
    void close() throws ContextException;
}
