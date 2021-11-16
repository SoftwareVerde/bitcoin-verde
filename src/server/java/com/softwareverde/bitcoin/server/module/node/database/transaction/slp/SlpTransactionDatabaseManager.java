package com.softwareverde.bitcoin.server.module.node.database.transaction.slp;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;

public interface SlpTransactionDatabaseManager {
    /**
     * Returns the cached SLP validity of the TransactionId.
     *  This function does run validation on the transaction and only queries its cached value.
     */
    Boolean getSlpTransactionValidationResult(TransactionId transactionId) throws DatabaseException;

    void setSlpTransactionValidationResult(TransactionId transactionId, Boolean isValid) throws DatabaseException;

    /**
     * Returns the TransactionIds of SLP transactions in the given block that have not been validated yet.
     *  Unconfirmed transactions are not returned by this function.
     */
    List<TransactionId> getConfirmedPendingValidationSlpTransactions(BlockId blockId) throws DatabaseException;

    BlockId getLastSlpValidatedBlockId() throws DatabaseException;

    void setLastSlpValidatedBlockId(BlockId blockId) throws DatabaseException;

    /**
     * Returns a list of (SLP) TransactionIds that have not been validated yet that reside in the mempool.
     */
    List<TransactionId> getUnconfirmedPendingValidationSlpTransactions(Integer maxCount) throws DatabaseException;

    /**
     * Removes all currently stored SLP validation results and deletes the last SLP-validated block ID property.
     */
    void deleteAllSlpValidationResults() throws DatabaseException;
}
