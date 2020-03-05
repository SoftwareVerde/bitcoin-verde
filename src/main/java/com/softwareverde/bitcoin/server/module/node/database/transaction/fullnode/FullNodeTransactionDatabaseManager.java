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
    Boolean previousOutputsExist(Transaction transaction) throws DatabaseException;
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
    TransactionOutput getUnspentTransactionOutput(TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException;

    /**
     * Returns a List of TransactionOutputs in the order provided by the `transactionOutputIdentifier` collection.
     *  If a provided TransactionOutputIdentifier corresponds to a non-existent or spent output, then null is put in its place within the returned list.
     *  If an error occurred while loading any TransactionOutput then null is returned instead of a List.
     */
    List<TransactionOutput> getUnspentTransactionOutputs(List<TransactionOutputIdentifier> transactionOutputIdentifier) throws DatabaseException;

    void markTransactionOutputsAsUnspent(List<TransactionOutputIdentifier> unspentTransactionOutputIdentifiers) throws DatabaseException;
    void markTransactionOutputsAsSpent(List<TransactionOutputIdentifier> spentTransactionOutputIdentifiers) throws DatabaseException;
    void commitUnspentTransactionOutputs() throws DatabaseException;
}
