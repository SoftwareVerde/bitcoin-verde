package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;

/**
 * Is functionally the same as a regular TransactionInputInflater, however additional checks are in place to assert
 *  that the inflated transaction is indeed a coinbase transaction.
 *  If these additional assertions fail, null is returned.
 */
public class CoinbaseTransactionInputInflater extends TransactionInputInflater {
    public static Boolean isCoinbaseInput(final TransactionInput transactionInput) {
        if (! Util.areEqual(transactionInput.getPreviousOutputTransactionHash(), Sha256Hash.EMPTY_HASH)) { return false; }
        if (transactionInput.getPreviousOutputIndex() != 0xFFFFFFFF) { return false; }
        if (transactionInput.getUnlockingScript().getByteCount() > 100) { return false; }

        return true;
    }

    @Override
    protected MutableTransactionInput _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final MutableTransactionInput transactionInput = super._fromByteArrayReader(byteArrayReader);

        if (! CoinbaseTransactionInputInflater.isCoinbaseInput(transactionInput)) { return null; }

        return transactionInput;
    }
}
