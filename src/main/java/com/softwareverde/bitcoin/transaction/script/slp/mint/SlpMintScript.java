package com.softwareverde.bitcoin.transaction.script.slp.mint;

import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScript;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptType;
import com.softwareverde.constable.Constable;
import com.softwareverde.util.Util;

import java.math.BigInteger;

public interface SlpMintScript extends SlpScript, Constable<ImmutableSlpMintScript> {
    Integer RECEIVER_TRANSACTION_OUTPUT_INDEX = 1;

    SlpTokenId getTokenId();
    Integer getBatonOutputIndex();
    BigInteger getTokenCount();

    @Override
    ImmutableSlpMintScript asConst();
}

abstract class SlpMintScriptCore implements SlpMintScript {
    protected SlpTokenId _tokenId;
    protected Integer _batonOutputIndex;
    protected BigInteger _tokenCount;

    public SlpMintScriptCore() { }

    public SlpMintScriptCore(final SlpMintScript slpMintScript) {
        _tokenId = slpMintScript.getTokenId().asConst();
        _batonOutputIndex = slpMintScript.getBatonOutputIndex();
        _tokenCount = slpMintScript.getTokenCount();
    }

    @Override
    public SlpScriptType getType() {
        return SlpScriptType.MINT;
    }

    @Override
    public Integer getMinimumTransactionOutputCount() {
        return Math.max(2, (Util.coalesce(_batonOutputIndex) + 1)); // Requires at least 1 Script Output and 1 Receiver Output...
    }

    @Override
    public SlpTokenId getTokenId() {
        return _tokenId;
    }

    @Override
    public Integer getBatonOutputIndex() {
        return _batonOutputIndex;
    }

    @Override
    public BigInteger getTokenCount() {
        return _tokenCount;
    }
}