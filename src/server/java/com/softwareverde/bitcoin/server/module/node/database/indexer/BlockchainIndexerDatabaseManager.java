package com.softwareverde.bitcoin.server.module.node.database.indexer;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.TypedAddress;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;

import java.util.Map;

public interface BlockchainIndexerDatabaseManager {
    List<TransactionId> getTransactionIds(BlockchainSegmentId blockchainSegmentId, TypedAddress address, Boolean includeUnconfirmedTransactions) throws DatabaseException;
    List<TransactionId> getTransactionIds(BlockchainSegmentId blockchainSegmentId, Sha256Hash scriptHash, Boolean includeUnconfirmedTransactions) throws DatabaseException;
    Long getAddressBalance(BlockchainSegmentId blockchainSegmentId, TypedAddress address, Boolean includeUnconfirmedTransactions) throws DatabaseException;
    Long getAddressBalance(BlockchainSegmentId blockchainSegmentId, Sha256Hash scriptHash, Boolean includeUnconfirmedTransactions) throws DatabaseException;
    Map<Integer, TransactionId> getTransactionsSpendingOutputsOf(TransactionId transactionId) throws DatabaseException;

    SlpTokenId getSlpTokenId(TransactionId transactionId) throws DatabaseException;
    List<TransactionId> getSlpTransactionIds(SlpTokenId slpTokenId) throws DatabaseException;

    void queueTransactionsForProcessing(List<TransactionId> transactionIds) throws DatabaseException;
    List<TransactionId> getUnprocessedTransactions(Integer batchSize) throws DatabaseException;
    void markTransactionProcessed(TransactionId transactionId) throws DatabaseException;

    void indexTransactionOutputs(List<TransactionId> transactionIds, List<Integer> outputIndexes, List<Long> amounts, List<ScriptType> scriptTypes, List<Address> addresses, List<Sha256Hash> scriptHashes, List<TransactionId> slpTransactionIds, List<ByteArray> memoActionTypes, List<ByteArray> memoActionIdentifiers) throws DatabaseException;
    void indexTransactionInputs(List<TransactionId> transactionIds, List<Integer> inputIndexes, List<TransactionOutputId> transactionOutputIds) throws DatabaseException;

    void deleteTransactionIndexes() throws DatabaseException;

    TransactionId getMostRecentTransactionId() throws DatabaseException;
    TransactionId getLastIndexedTransactionId() throws DatabaseException;
}
