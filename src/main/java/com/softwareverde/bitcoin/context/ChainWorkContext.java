package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;

public interface ChainWorkContext {
    ChainWork getChainWork(Long blockHeight);
}
