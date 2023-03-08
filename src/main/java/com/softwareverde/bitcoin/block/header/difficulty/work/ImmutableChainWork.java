package com.softwareverde.bitcoin.block.header.difficulty.work;

import com.softwareverde.constable.Const;

public class ImmutableChainWork extends ImmutableWork implements ChainWork, Const {
    public ImmutableChainWork(final ChainWork chainWork) {
        super(chainWork);
    }

    @Override
    public ImmutableChainWork asConst() {
        return this;
    }
}
