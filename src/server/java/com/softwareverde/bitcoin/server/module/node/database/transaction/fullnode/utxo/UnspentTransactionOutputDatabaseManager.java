package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.UnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;

public interface UnspentTransactionOutputDatabaseManager {
    interface SpentState {
        Boolean isSpent();
        Boolean isFlushedToDisk();
    }

    Long DEFAULT_MAX_UTXO_CACHE_COUNT = 500000L;
    Float DEFAULT_PURGE_PERCENT = 0.5F;
    Long BYTES_PER_UTXO = 128L; // NOTE: This value is larger than the actual size.  // TODO: Research a more accurate UTXO byte count.

    void markTransactionOutputsAsSpent(List<TransactionOutputIdentifier> spentTransactionOutputIdentifiers) throws DatabaseException;
    void insertUnspentTransactionOutputs(List<TransactionOutputIdentifier> unspentTransactionOutputIdentifiers, List<TransactionOutput> transactionOutputs, Long blockHeight, Sha256Hash coinbaseTransactionHash) throws DatabaseException;

    /**
     * Marks the provided UTXOs as spent, logically removing them from the UTXO set, and forces the outputs to be synchronized to disk on the next UTXO commit.
     */
    void undoCreationOfTransactionOutputs(List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException;

    /**
     * Re-inserts the provided UTXOs into the UTXO set and looks up their original associated blockHeight.  These UTXOs will be synchronized to disk during the next UTXO commit.
     */
    void undoSpendingOfTransactionOutputs(List<TransactionOutputIdentifier> transactionOutputIdentifiers, BlockchainSegmentId blockchainSegmentId) throws DatabaseException;

    UnspentTransactionOutput getUnspentTransactionOutput(TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException;

    /**
     * Returns the UnspentTransactionOutput associated with the provided TransactionOutputIdentifier, or null if it does not exist or is spent.
     *  loadUnspentTransactionOutput, unlike getUnspentTransactionOutput, ensures the UTXO is cached within the UTXO, if the implementation supports caching and the UTXO exists.
     */
    UnspentTransactionOutput loadUnspentTransactionOutput(TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException;

    List<UnspentTransactionOutput> getUnspentTransactionOutputs(List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException;
    // List<UnspentTransactionOutput> loadUnspentTransactionOutputs(List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException;

    /**
     * Searches the Committed UTXO set for TransactionOutputIdentifiers belonging to the provided transactionHash.
     *  Identifiers may be spent or unspent, depending on the synchronization state of the chain and the disk flush state.
     *  This function returns identifiers on a best-effort basis and should not be used for enforcing consensus.
     */
    List<TransactionOutputIdentifier> getFastSyncOutputIdentifiers(Sha256Hash transactionHash) throws DatabaseException;

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

    UnspentTransactionOutput findOutputData(TransactionOutputIdentifier transactionOutputIdentifier, BlockchainSegmentId blockchainSegmentId) throws DatabaseException;

    void visitUnspentTransactionOutputs(UnspentTransactionOutputVisitor visitor) throws DatabaseException;
}
