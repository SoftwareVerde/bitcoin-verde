package com.softwareverde.bitcoin.server.module.node.database.indexer;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;

public interface TransactionOutputDatabaseManager {
    AddressId storeAddress(Address address) throws DatabaseException;
    AddressId getAddressId(Address address) throws DatabaseException;
    List<AddressId> getAddressIds(TransactionId transactionId) throws DatabaseException;

    List<TransactionId> getTransactionIds(BlockchainSegmentId blockchainSegmentId, AddressId addressId, Boolean includeUnconfirmedTransactions) throws DatabaseException;
    List<TransactionId> getTransactionIdsSendingTo(BlockchainSegmentId blockchainSegmentId, AddressId addressId, Boolean includeUnconfirmedTransactions) throws DatabaseException;
    List<TransactionId> getTransactionIdsSpendingFrom(BlockchainSegmentId blockchainSegmentId, AddressId addressId, Boolean includeUnconfirmedTransactions) throws DatabaseException;
    Long getAddressBalance(BlockchainSegmentId blockchainSegmentId, AddressId addressId) throws DatabaseException;

    SlpTokenId getSlpTokenId(TransactionId transactionId) throws DatabaseException;
    List<TransactionId> getSlpTransactionIds(SlpTokenId slpTokenId) throws DatabaseException;

    void queueTransactionsForProcessing(List<TransactionId> transactionIds) throws DatabaseException;
    List<TransactionId> getUnprocessedTransactions(Integer batchSize) throws DatabaseException;
    void dequeueTransactionsForProcessing(List<TransactionId> transactionIds) throws DatabaseException;

    void indexTransactionOutput(TransactionId transactionId, Integer outputIndex, Long amount, ScriptType scriptType, AddressId addressId, TransactionId slpTransactionId) throws DatabaseException;
}
