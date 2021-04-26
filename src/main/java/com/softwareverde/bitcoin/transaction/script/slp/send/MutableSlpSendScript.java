package com.softwareverde.bitcoin.transaction.script.slp.send;

import com.softwareverde.bitcoin.slp.SlpTokenId;

import java.math.BigInteger;

public class MutableSlpSendScript extends SlpSendScriptCore {

    public MutableSlpSendScript() { }

    public MutableSlpSendScript(final SlpSendScript slpSendScript) {
        super(slpSendScript);
    }

    public void setTokenId(final SlpTokenId tokenId) {
        _tokenId = tokenId;
    }

    /**
     * Sets the spendAmount.
     *  If transactionOutputIndex is less than 0 or greater than or equal to MAX_OUTPUT_COUNT, an IndexOutOfBounds exception is thrown.
     *  Attempting to set the 0th index does nothing.
     *  Setting an amount to a non-null value sets all lesser indexes to zero if they have not been set.
     *  Setting an amount to null will unset all indexes after transactionOutputIndex.
     */
    public void setAmount(final Integer transactionOutputIndex, final BigInteger amount) {
        if (transactionOutputIndex < 0) { throw new IndexOutOfBoundsException(); }
        if (transactionOutputIndex >= MAX_OUTPUT_COUNT) { throw new IndexOutOfBoundsException(); }
        if (transactionOutputIndex == 0) { return; }

        _amounts[transactionOutputIndex] = amount;

        { // Maintain integrity...
            if (amount == null) {
                for (int i = (transactionOutputIndex + 1); i < MAX_OUTPUT_COUNT; ++i) {
                    _amounts[i] = null;
                }
            }
            else {
                for (int i = 1; i < transactionOutputIndex; ++i) {
                    if (_amounts[i] == null) {
                        _amounts[i] = BigInteger.ZERO;
                    }
                }
            }
        }
    }

    @Override
    public ImmutableSlpSendScript asConst() {
        return new ImmutableSlpSendScript(this);
    }
}
