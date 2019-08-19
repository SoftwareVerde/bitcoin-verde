package com.softwareverde.bitcoin.slp.validator;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;

public interface SlpTransactionValidationCache {
    /**
     * Returns null if validity of the associated transaction has not been cached.
     * Returns true if validity of the associated transaction has been cached and is valid.
     * Returns false if validity of the associated transaction has been cached and is invalid.
     */
    Boolean isValid(Sha256Hash transactionHash);

    /**
     * Marks the transaction as valid for subsequent calls to ::isValid.
     *  Failing to mark a transaction as valid/invalid may result in [sum(txInputCount)]^[validationDepth].
     */
    void setIsValid(Sha256Hash transactionHash, Boolean isValid);
}
