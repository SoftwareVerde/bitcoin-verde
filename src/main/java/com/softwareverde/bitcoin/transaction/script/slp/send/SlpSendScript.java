package com.softwareverde.bitcoin.transaction.script.slp.send;

import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScript;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptType;
import com.softwareverde.constable.Constable;

import java.math.BigInteger;

public interface SlpSendScript extends SlpScript, Constable<ImmutableSlpSendScript> {
    Integer MAX_OUTPUT_COUNT = 20;

    SlpTokenId getTokenId();
    BigInteger getAmount(Integer transactionOutputIndex);
    BigInteger getTotalAmount();
}

abstract class SlpSendScriptCore implements SlpSendScript {
    protected SlpTokenId _tokenId;
    protected final BigInteger[] _amounts = new BigInteger[MAX_OUTPUT_COUNT];

    public SlpSendScriptCore() { }

    public SlpSendScriptCore(final SlpSendScript slpSendScript) {
        _tokenId = slpSendScript.getTokenId().asConst();

        for (int i = 0; i < MAX_OUTPUT_COUNT; ++i) {
            _amounts[i] = slpSendScript.getAmount(i);
        }
    }

    @Override
    public SlpScriptType getType() {
        return SlpScriptType.SEND;
    }

    @Override
    public Integer getMinimumTransactionOutputCount() {
        int tokenOutputCount = 0;
        for (int i = 0; i < MAX_OUTPUT_COUNT; ++i) {
            final BigInteger amount = _amounts[i];
            if (amount == null) { break; }

            tokenOutputCount += 1;
        }

        return (tokenOutputCount + 1); // Requires the number of outputs specified in the SpendScript and one for the Script itself.
    }

    @Override
    public SlpTokenId getTokenId() {
        return _tokenId;
    }

    @Override
    public BigInteger getAmount(final Integer transactionOutputIndex) {
        if (transactionOutputIndex >= MAX_OUTPUT_COUNT) { return null; }
        if (transactionOutputIndex < 0) { throw new IndexOutOfBoundsException(); }
        if (transactionOutputIndex == 0) { return null; }

        return _amounts[transactionOutputIndex];
    }

    @Override
    public BigInteger getTotalAmount() {
        BigInteger totalAmount = BigInteger.ZERO;
        for (int i = 0; i < MAX_OUTPUT_COUNT; ++i) {
            final BigInteger amount = _amounts[i];
            if (amount == null) { continue; }

            totalAmount = totalAmount.add(amount);
        }

        return totalAmount;
    }
}