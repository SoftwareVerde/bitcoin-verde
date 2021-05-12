package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.UnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public interface UnspentTransactionOutputDatabaseManager {
    interface SpentState {
        Boolean isSpent();
        Boolean isFlushedToDisk();
    }

    Long DEFAULT_MAX_UTXO_CACHE_COUNT = 500000L;
    Float DEFAULT_PURGE_PERCENT = 0.5F;
    Long BYTES_PER_UTXO = 128L; // NOTE: This value is larger than the actual size.  // TODO: Research a more accurate UTXO byte count.

    ReentrantReadWriteLock.ReadLock UTXO_READ_MUTEX = UtxoCacheStaticState.READ_LOCK;
    ReentrantReadWriteLock.WriteLock UTXO_WRITE_MUTEX = UtxoCacheStaticState.WRITE_LOCK;

    static void lockUtxoSet() {
        UTXO_WRITE_MUTEX.lock();
    }

    static void unlockUtxoSet() {
        UTXO_WRITE_MUTEX.unlock();
    }

    /**
     * Marks the UTXO set as invalid, equivalently putting it in an error-state.
     *  Once invalidated, UTXOs may not be accessed until it is reset via ::clearUncommittedUtxoSet.
     * This function cannot throw and ensures the current UTXO will not be committed to disk.
     */
    static void invalidateUncommittedUtxoSet() {
        // First immediately invalidate the UTXO set without a lock, to ensure the set cannot be committed to desk, even upon deadlock or error.
        UtxoCacheStaticState.UNCOMMITTED_UTXO_BLOCK_HEIGHT.value = -1L;

        // Second, acquire the write lock and re-set the invalidation state to ensure any functions mid-execution did not accidentally clear the above invalidation.
        UTXO_WRITE_MUTEX.lock();
        try {
            UtxoCacheStaticState.UNCOMMITTED_UTXO_BLOCK_HEIGHT.value = -1L;
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    static Boolean isUtxoCacheReady() {
        return UtxoCacheStaticState.isUtxoCacheReady();
    }

    void markTransactionOutputsAsSpent(List<TransactionOutputIdentifier> spentTransactionOutputIdentifiers) throws DatabaseException;
    void insertUnspentTransactionOutputs(List<TransactionOutputIdentifier> unspentTransactionOutputIdentifiers, List<TransactionOutput> transactionOutputs, Long blockHeight) throws DatabaseException;

    /**
     * Marks the provided UTXOs as spent, logically removing them from the UTXO set, and forces the outputs to be synchronized to disk on the next UTXO commit.
     */
    void undoCreationOfTransactionOutputs(List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException;

    /**
     * Re-inserts the provided UTXOs into the UTXO set and looks up their original associated blockHeight.  These UTXOs will be synchronized to disk during the next UTXO commit.
     */
    void undoSpendingOfTransactionOutputs(List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException;

    UnspentTransactionOutput getUnspentTransactionOutput(TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException;

    /**
     * Returns the UnspentTransactionOutput associated with the provided TransactionOutputIdentifier, or null if it does not exist or is spent.
     *  loadUnspentTransactionOutput, unlike getUnspentTransactionOutput, ensures the UTXO is cached within the UTXO, if the implementation supports caching and the UTXO exists.
     */
    UnspentTransactionOutput loadUnspentTransactionOutput(TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException;

    List<UnspentTransactionOutput> getUnspentTransactionOutputs(List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException;
    // List<UnspentTransactionOutput> loadUnspentTransactionOutputs(List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException;

    /**
     * Flushes all queued UTXO set changes to disk.  The UTXO set is locked during commit duration.
     *  Returns true if the UTXO set was committed or false if it was not (i.e. if CommitAsyncMode.SKIP_IF_BUSY was provided).
     */
    Boolean commitUnspentTransactionOutputs(DatabaseManagerFactory databaseManagerFactory, CommitAsyncMode commitAsyncMode) throws DatabaseException;

    Long getUncommittedUnspentTransactionOutputCount() throws DatabaseException;
    Long getUncommittedUnspentTransactionOutputCount(Boolean noLock) throws DatabaseException;

    Long getCommittedUnspentTransactionOutputBlockHeight() throws DatabaseException;
    Long getCommittedUnspentTransactionOutputBlockHeight(Boolean noLock) throws DatabaseException;

    void setUncommittedUnspentTransactionOutputBlockHeight(Long blockHeight) throws DatabaseException;

    Long getUncommittedUnspentTransactionOutputBlockHeight() throws DatabaseException;
    Long getUncommittedUnspentTransactionOutputBlockHeight(Boolean noLock) throws DatabaseException;

    void clearCommittedUtxoSet() throws DatabaseException;

    void clearUncommittedUtxoSet() throws DatabaseException;

    Long getMaxUtxoCount();
}
