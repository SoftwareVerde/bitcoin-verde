package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode;

import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.security.hash.sha256.Sha256Hash;

public interface FullNodeTransactionDatabaseManager extends TransactionDatabaseManager {
    Transaction getTransaction(TransactionId transactionId, Boolean shouldUpdateUnspentOutputCache) throws DatabaseException;
    Boolean previousOutputsExist(Transaction transaction) throws DatabaseException;
    void addToUnconfirmedTransactions(TransactionId transactionId) throws DatabaseException;
    void addToUnconfirmedTransactions(List<TransactionId> transactionIds) throws DatabaseException;
    void removeFromUnconfirmedTransactions(TransactionId transactionId) throws DatabaseException;
    void removeFromUnconfirmedTransactions(List<TransactionId> transactionIds) throws DatabaseException;
    Boolean isUnconfirmedTransaction(TransactionId transactionId) throws DatabaseException;
    List<TransactionId> getUnconfirmedTransactionIds() throws DatabaseException;

    // "Select transactions that are unconfirmed that spend an output spent by any of these transactionIds..."
    List<TransactionId> getUnconfirmedTransactionsDependingOnSpentInputsOf(List<TransactionId> transactionIds) throws DatabaseException;

    // "Select transactions that are unconfirmed that spent an output produced by any of these transactionIds..."
    List<TransactionId> getUnconfirmedTransactionsDependingOn(List<TransactionId> transactionIds) throws DatabaseException;

    Integer getUnconfirmedTransactionCount() throws DatabaseException;
    Long calculateTransactionFee(Transaction transaction) throws DatabaseException;

    SlpTokenId getSlpTokenId(Sha256Hash transactionHash) throws DatabaseException;

    TransactionOutput getTransactionOutput(TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException;
    TransactionOutput getUnspentTransactionOutput(TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException;
}
