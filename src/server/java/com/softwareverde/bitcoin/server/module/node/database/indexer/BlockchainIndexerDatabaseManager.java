package com.softwareverde.bitcoin.server.module.node.database.indexer;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;

public interface BlockchainIndexerDatabaseManager {
    List<TransactionId> getTransactionIds(BlockchainSegmentId blockchainSegmentId, Address address, Boolean includeUnconfirmedTransactions) throws DatabaseException;
    Long getAddressBalance(BlockchainSegmentId blockchainSegmentId, Address address) throws DatabaseException;

    SlpTokenId getSlpTokenId(TransactionId transactionId) throws DatabaseException;
    List<TransactionId> getSlpTransactionIds(SlpTokenId slpTokenId) throws DatabaseException;

    void queueTransactionsForProcessing(List<TransactionId> transactionIds) throws DatabaseException;
    List<TransactionId> getUnprocessedTransactions(Integer batchSize) throws DatabaseException;
    void dequeueTransactionsForProcessing(List<TransactionId> transactionIds) throws DatabaseException;

    void indexTransactionOutputs(List<TransactionId> transactionIds, List<Integer> outputIndexes, List<Long> amounts, List<ScriptType> scriptTypes, List<Address> addresses, List<TransactionId> slpTransactionIds, List<ByteArray> memoActionTypes, List<ByteArray> memoActionIdentifiers) throws DatabaseException;
    void indexTransactionInputs(List<TransactionId> transactionIds, List<Integer> inputIndexes, List<TransactionOutputId> transactionOutputIds) throws DatabaseException;

    void deleteTransactionIndexes() throws DatabaseException;
}
