package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.util.Util;

/**
 * Is functionally the same as a regular TransactionInputInflater, however additional checks are in place to assert
 *  that the inflated transaction is indeed a coinbase transaction.
 *  If these additional assertions fail, null is returned.
 */
public class CoinbaseTransactionInputInflater extends TransactionInputInflater {
    public static Boolean isCoinbaseInput(final TransactionInput transactionInput) {
        if (! Util.areEqual(transactionInput.getPreviousOutputTransactionHash(), Sha256Hash.EMPTY_HASH)) { return false; }

        // TODO: Validate the following rules are applied since BlockHeight 0...
        // if (transactionInput.getPreviousOutputIndex() != 0xFFFFFFFF) { return false; }
        // if (transactionInput.getUnlockingScript().getByteCount() > 100) { return false; }
        // TODO: Check if the signature script includes a blockHeight value (as of Transaction v2)...

        return true;
    }

    @Override
    protected MutableTransactionInput _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final MutableTransactionInput transactionInput = super._fromByteArrayReader(byteArrayReader);

        if (! CoinbaseTransactionInputInflater.isCoinbaseInput(transactionInput)) { return null; }

        return transactionInput;
    }
}
