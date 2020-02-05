package com.softwareverde.bitcoin.slp.validator;

import com.softwareverde.security.hash.sha256.Sha256Hash;

import java.util.HashMap;

public class HashMapSlpTransactionValidationCache implements SlpTransactionValidationCache {
    protected final HashMap<Sha256Hash, Boolean> _validatedTransactions = new HashMap<Sha256Hash, Boolean>();

    @Override
    public Boolean isValid(final Sha256Hash transactionHash) {
        return _validatedTransactions.get(transactionHash);
    }

    @Override
    public void setIsValid(final Sha256Hash transactionHash, final Boolean isValid) {
        _validatedTransactions.put(transactionHash, isValid);
    }
}
