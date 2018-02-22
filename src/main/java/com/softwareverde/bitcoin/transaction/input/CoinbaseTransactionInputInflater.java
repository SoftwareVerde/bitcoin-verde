package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;

/**
 * Is functionally the same as a regular TransactionInputInflater, however additional checks are in place to assert
 *  that the inflated transaction is indeed a coinbase transaction.
 *  If these additional assertions fail, null is returned.
 */
public class CoinbaseTransactionInputInflater extends TransactionInputInflater {
    protected Boolean _areAllBytesEqualTo(final byte[] bytes, final byte requiredValue) {
        for (int i=0; i<bytes.length; ++i) {
            final byte b = bytes[i];
            if (b != requiredValue) { return false; }
        }
        return true;
    }

    @Override
    protected MutableTransactionInput _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final MutableTransactionInput transactionInput = super._fromByteArrayReader(byteArrayReader);

        if (! _areAllBytesEqualTo(transactionInput._previousTransactionOutputHash.getBytes(), (byte) 0x00)) { return null; }
        if (transactionInput._previousTransactionOutputIndex != 0xFFFFFFFF) { return null; }
        if (transactionInput._unlockingScript.length > 100) { return null; }

        // TODO: The signature script must include a blockHeight value as of Transaction Version 2.

        return transactionInput;
    }
}
