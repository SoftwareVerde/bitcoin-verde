package com.softwareverde.bitcoin.transaction.script.slp.commit;

import com.softwareverde.constable.Const;

public class ImmutableSlpCommitScript extends SlpCommitScriptCore implements Const {

    public ImmutableSlpCommitScript(final SlpCommitScript slpCommitScript) {
        super(slpCommitScript);
    }

    @Override
    public ImmutableSlpCommitScript asConst() {
        return this;
    }
}
