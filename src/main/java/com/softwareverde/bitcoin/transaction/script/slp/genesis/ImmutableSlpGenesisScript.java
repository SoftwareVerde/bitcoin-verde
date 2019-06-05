package com.softwareverde.bitcoin.transaction.script.slp.genesis;

import com.softwareverde.constable.Const;

public class ImmutableSlpGenesisScript extends SlpGenesisScriptCore implements Const {

    public ImmutableSlpGenesisScript(final SlpGenesisScript slpGenesisScript) {
        super(slpGenesisScript);
    }

    @Override
    public ImmutableSlpGenesisScript asConst() {
        return this;
    }
}
