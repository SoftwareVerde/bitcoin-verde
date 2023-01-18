package com.softwareverde.bitcoin.slp.validator;

import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class HashMapSlpTransactionValidationCache implements SlpTransactionValidationCache {
    protected final MutableHashMap<Sha256Hash, Boolean> _validatedTransactions = new MutableHashMap<>();

    @Override
    public Boolean isValid(final Sha256Hash transactionHash) {
        return _validatedTransactions.get(transactionHash);
    }

    @Override
    public void setIsValid(final Sha256Hash transactionHash, final Boolean isValid) {
        _validatedTransactions.put(transactionHash, isValid);
    }
}
