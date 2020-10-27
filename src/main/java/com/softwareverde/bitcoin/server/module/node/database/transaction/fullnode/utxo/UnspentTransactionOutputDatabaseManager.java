package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public interface UnspentTransactionOutputDatabaseManager {
    Long DEFAULT_MAX_UTXO_CACHE_COUNT = 500000L;
    Float DEFAULT_PURGE_PERCENT = 0.5F;
    Long BYTES_PER_UTXO = 128L; // TODO

    interface SpentState {
        Boolean isSpent();
        Boolean isFlushedToDisk();
    }

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
        UnspentTransactionOutputJvmManager.UNCOMMITTED_UTXO_BLOCK_HEIGHT.value = -1L;

        // Second, acquire the write lock and re-set the invalidation state to ensure any functions mid-execution did not accidentally clear the above invalidation.
        UTXO_WRITE_MUTEX.lock();
        try {
            UnspentTransactionOutputJvmManager.UNCOMMITTED_UTXO_BLOCK_HEIGHT.value = -1L;
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    ReentrantReadWriteLock.ReadLock UTXO_READ_MUTEX = UnspentTransactionOutputJvmManager.READ_MUTEX;
    ReentrantReadWriteLock.WriteLock UTXO_WRITE_MUTEX = UnspentTransactionOutputJvmManager.WRITE_MUTEX;

    void markTransactionOutputsAsSpent(List<TransactionOutputIdentifier> spentTransactionOutputIdentifiers) throws DatabaseException;
    void insertUnspentTransactionOutputs(List<TransactionOutputIdentifier> unspentTransactionOutputIdentifiers, Long blockHeight) throws DatabaseException;

    /**
     * Marks the provided UTXOs as spent, logically removing them from the UTXO set, and forces the outputs to be synchronized to disk on the next UTXO commit.
     */
    void undoCreationOfTransactionOutputs(List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException;

    /**
     * Re-inserts the provided UTXOs into the UTXO set and looks up their original associated blockHeight.  These UTXOs will be synchronized to disk during the next UTXO commit.
     */
    void undoSpendingOfTransactionOutputs(List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException;

    TransactionOutput getUnspentTransactionOutput(TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException;

    List<TransactionOutput> getUnspentTransactionOutputs(List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException;

    /**
     * Flushes all queued UTXO set changes to disk.  The UTXO set is locked for the duration of this call.
     */
    void commitUnspentTransactionOutputs() throws DatabaseException;

    Long getUncommittedUnspentTransactionOutputCount() throws DatabaseException;
    Long getUncommittedUnspentTransactionOutputCount(Boolean noLock) throws DatabaseException;

    Long getCommittedUnspentTransactionOutputBlockHeight() throws DatabaseException;
    Long getCommittedUnspentTransactionOutputBlockHeight(Boolean noLock) throws DatabaseException;

    void setUncommittedUnspentTransactionOutputBlockHeight(Long blockHeight) throws DatabaseException;

    Long getUnspentTransactionOutputBlockHeight() throws DatabaseException;
    Long getUnspentTransactionOutputBlockHeight(Boolean noLock) throws DatabaseException;

    void clearCommittedUtxoSet() throws DatabaseException;

    void clearUncommittedUtxoSet() throws DatabaseException;

    Long getMaxUtxoCount();
}
