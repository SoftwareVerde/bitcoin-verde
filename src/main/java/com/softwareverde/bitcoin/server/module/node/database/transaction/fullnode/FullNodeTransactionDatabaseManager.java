package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode;

import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;

public interface FullNodeTransactionDatabaseManager extends TransactionDatabaseManager {
    Boolean isCoinbaseTransaction(Sha256Hash transactionHash) throws DatabaseException;

    TransactionId storeTransactionHash(Transaction transaction) throws DatabaseException;
    List<TransactionId> storeTransactionHashes(List<Transaction> transactions) throws DatabaseException;
    List<TransactionId> storeTransactionHashes(List<Transaction> transactions, DatabaseConnectionFactory databaseConnectionFactory, Integer maxConnectionCount) throws DatabaseException;
    Boolean previousOutputsExist(Transaction transaction) throws DatabaseException;

    TransactionId storeUnconfirmedTransaction(Transaction transaction) throws DatabaseException;
    List<TransactionId> storeUnconfirmedTransactions(List<Transaction> transactions) throws DatabaseException;
    void addToUnconfirmedTransactions(TransactionId transactionId) throws DatabaseException;
    void addToUnconfirmedTransactions(List<TransactionId> transactionIds) throws DatabaseException;
    void removeFromUnconfirmedTransactions(TransactionId transactionId) throws DatabaseException;
    void removeFromUnconfirmedTransactions(List<TransactionId> transactionIds) throws DatabaseException;
    Boolean isUnconfirmedTransaction(TransactionId transactionId) throws DatabaseException;
    List<TransactionId> getUnconfirmedTransactionIds() throws DatabaseException;

    // "Select transactions that are unconfirmed that spend an output spent by any of these transactionIds..."
    List<TransactionId> getUnconfirmedTransactionsDependingOnSpentInputsOf(List<Transaction> transactions) throws DatabaseException;

    // "Select transactions that are unconfirmed that spent an output produced by any of these transactionIds..."
    List<TransactionId> getUnconfirmedTransactionsDependingOn(List<TransactionId> transactionIds) throws DatabaseException;

    Integer getUnconfirmedTransactionCount() throws DatabaseException;
    Long calculateTransactionFee(Transaction transaction) throws DatabaseException;

    SlpTokenId getSlpTokenId(Sha256Hash transactionHash) throws DatabaseException;

    TransactionOutput getTransactionOutput(TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException;
}
