package com.softwareverde.bitcoin.server.module.node.database.indexer;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;

import java.util.Map;

public interface BlockchainIndexerDatabaseManager {
    AddressId storeAddress(Address address) throws DatabaseException;
    Map<Address, AddressId> storeAddresses(List<Address> addresses) throws DatabaseException;
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

    void indexTransactionOutputs(List<TransactionId> transactionIds, List<Integer> outputIndexes, List<Long> amounts, List<ScriptType> scriptTypes, List<AddressId> addressIds, List<TransactionId> slpTransactionIds) throws DatabaseException;
    void indexTransactionInputs(List<TransactionId> transactionIds, List<Integer> inputIndexes, List<AddressId> addressIds) throws DatabaseException;
}
