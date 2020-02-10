package com.softwareverde.bitcoin.server.module.node.database.transaction.slp;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.LockingScriptId;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.slp.genesis.SlpGenesisScript;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;

import java.util.LinkedHashMap;

public interface SlpTransactionDatabaseManager {
    /**
     * Returns the cached SLP validity of the TransactionId.
     *  This function does run validation on the transaction and only queries its cached value.
     */
    Boolean getSlpTransactionValidationResult(BlockchainSegmentId blockchainSegmentId, TransactionId transactionId) throws DatabaseException;

    void setSlpTransactionValidationResult(BlockchainSegmentId blockchainSegmentId, TransactionId transactionId, Boolean isValid) throws DatabaseException;

    TransactionId calculateSlpTokenGenesisTransactionId(LockingScriptId lockingScriptId, LockingScript nullableLockingScript) throws DatabaseException;

    TransactionId calculateSlpTokenGenesisTransactionId(TransactionId transactionId, LockingScript nullableLockingScript) throws DatabaseException;

    SlpTokenId getSlpTokenId(Object transactionOutputId) throws DatabaseException;

    SlpGenesisScript getSlpGenesisScript(SlpTokenId slpTokenId) throws DatabaseException;

    SlpTokenId getSlpTokenId(TransactionId transactionId) throws DatabaseException;

    void setSlpTransactionId(TransactionId slpTokenTransactionId, Object transactionOutputId) throws DatabaseException;

    void setSlpTransactionIds(TransactionId slpTokenTransactionId, List<Object> transactionOutputIds) throws DatabaseException;

    /**
     * Returns a mapping of (SLP) TransactionIds that have not been validated yet, ordered by their respective block's height.
     *  Unconfirmed transactions are not returned by this function.
     */
    LinkedHashMap<BlockId, List<TransactionId>> getConfirmedPendingValidationSlpTransactions(Integer maxCount) throws DatabaseException;

    /**
     * Returns a list of (SLP) TransactionIds that have not been validated yet that reside in the mempool.
     */
    List<TransactionId> getUnconfirmedPendingValidationSlpTransactions(Integer maxCount) throws DatabaseException;
}
