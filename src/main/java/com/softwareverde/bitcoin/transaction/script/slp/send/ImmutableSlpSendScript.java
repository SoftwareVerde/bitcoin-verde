package com.softwareverde.bitcoin.transaction.script.slp.send;

import com.softwareverde.constable.Const;

public class ImmutableSlpSendScript extends SlpSendScriptCore implements Const {
    public ImmutableSlpSendScript(final SlpSendScript slpSendScript) {
        super(slpSendScript);
    }

    @Override
    public ImmutableSlpSendScript asConst() {
        return this;
    }
}
