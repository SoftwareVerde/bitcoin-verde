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
    String UTXO_CACHE_BLOCK_HEIGHT_KEY = "utxo_cache_block_height";

    /**
     * MAX_UTXO_CACHE_COUNT determines the maximum number of rows (both clean and dirty) within the unspent_transaction_outputs in-memory table.
     *  This value is a function of the max_heap_table_size, which caps out at 4 gigabytes.
     *  The theoretical value, calculated via (unspent_transaction_outputs::max_data_length / unspent_transaction_outputs::avg_row_length), is not quite accurate.
     *  After some empirical evidence, the actual unspent_transaction_outputs::max_data_length and unspent_transaction_outputs::avg_row_length reported by MySQL aren't sufficient/accurate.
     *  The actual observed max row count is 39216366, which renders exactly 4GB of memory (1882505376 in data, 2412509502 in indexes), which puts row_length at 48 bytes per row, 109.55 including indexes.
     *  The value chosen, 33554432 (2^25), is the closest power-of-two under the 4GB max, which allows for some additional (although unobserved) inaccuracies.
     *
     *  Update: Using a BTREE for the PRIMARY KEY changes the used bytes per row (BTREE is less memory-efficient but more performant).
     *      The actual observed max row count with these settings is 27891486, which results in 1338877344 in data, 2941008296 in indexes). 48 bytes per row, 154 including indexes.
     *      The new value chosen is not near a clean power of two, so 27M was chosen (27889398 being the theoretical max).
     */
    Long MAX_UTXO_CACHE_COUNT = 27000000L;

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

    void markTransactionOutputsAsUnspent(List<TransactionOutputIdentifier> unspentTransactionOutputIdentifiers, final Long blockHeight) throws DatabaseException;
    void markTransactionOutputsAsSpent(List<TransactionOutputIdentifier> spentTransactionOutputIdentifiers, final Long blockHeight) throws DatabaseException;
    void commitUnspentTransactionOutputs() throws DatabaseException;
    Long getUncommittedUnspentTransactionOutputCount() throws DatabaseException;
    Long getCommittedUnspentTransactionOutputBlockHeight() throws DatabaseException;
}
