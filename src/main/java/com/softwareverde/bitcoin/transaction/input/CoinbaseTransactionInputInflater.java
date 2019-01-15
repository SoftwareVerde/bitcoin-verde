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
    @Override
    protected MutableTransactionInput _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final MutableTransactionInput transactionInput = super._fromByteArrayReader(byteArrayReader);

        if (! Util.areEqual(transactionInput._previousOutputTransactionHash, Sha256Hash.EMPTY_HASH)) { return null; }
        if (transactionInput._previousOutputIndex != 0xFFFFFFFF) { return null; }
        if (transactionInput._unlockingScript.getByteCount() > 100) { return null; }

        // TODO: The signature script must include a blockHeight value as of Transaction Version 2.

        return transactionInput;
    }
}
