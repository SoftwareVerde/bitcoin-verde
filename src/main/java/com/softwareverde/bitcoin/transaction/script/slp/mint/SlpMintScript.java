package com.softwareverde.bitcoin.transaction.script.slp.mint;

import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.constable.Constable;

public interface SlpMintScript extends Constable<ImmutableSlpMintScript> {
    Integer RECEIVER_TRANSACTION_OUTPUT_INDEX = 1;

    SlpTokenId getTokenId();
    Integer getGeneratorOutputIndex();
    Long getTokenCount();

    @Override
    ImmutableSlpMintScript asConst();
}

abstract class SlpMintScriptCore implements SlpMintScript {
    protected SlpTokenId _tokenId;
    protected Integer _generatorOutputIndex;
    protected Long _tokenCount;

    public SlpMintScriptCore() { }

    public SlpMintScriptCore(final SlpMintScript slpMintScript) {
        _tokenId = slpMintScript.getTokenId().asConst();
        _generatorOutputIndex = slpMintScript.getGeneratorOutputIndex();
        _tokenCount = slpMintScript.getTokenCount();
    }

    @Override
    public SlpTokenId getTokenId() {
        return _tokenId;
    }

    @Override
    public Integer getGeneratorOutputIndex() {
        return _generatorOutputIndex;
    }

    @Override
    public Long getTokenCount() {
        return _tokenCount;
    }
}