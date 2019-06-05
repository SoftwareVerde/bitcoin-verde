package com.softwareverde.bitcoin.transaction.script.slp.mint;

import com.softwareverde.constable.Const;

public class ImmutableSlpMintScript extends SlpMintScriptCore implements Const {

    public ImmutableSlpMintScript(final SlpMintScript slpMintScript) {
        super(slpMintScript);
    }

    @Override
    public ImmutableSlpMintScript asConst() {
        return this;
    }
}
