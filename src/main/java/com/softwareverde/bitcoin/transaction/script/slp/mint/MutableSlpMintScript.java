package com.softwareverde.bitcoin.transaction.script.slp.mint;

import com.softwareverde.bitcoin.slp.SlpTokenId;

import java.math.BigInteger;

public class MutableSlpMintScript extends SlpMintScriptCore {

    public MutableSlpMintScript() { }

    public MutableSlpMintScript(final SlpMintScript slpMintScript) {
        super(slpMintScript);
    }

    public void setTokenId(final SlpTokenId tokenId) {
        _tokenId = tokenId;
    }

    public void setBatonOutputIndex(final Integer batonOutputIndex) {
        _batonOutputIndex = batonOutputIndex;
    }

    public void setTokenCount(final BigInteger tokenCount) {
        _tokenCount = tokenCount;
    }

    @Override
    public ImmutableSlpMintScript asConst() {
        return new ImmutableSlpMintScript(this);
    }
}
