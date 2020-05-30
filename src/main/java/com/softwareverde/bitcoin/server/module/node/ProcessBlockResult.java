package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;

public class ProcessBlockResult {
    public final Block block;
    public final Long blockHeight;
    public final Boolean isValid;
    public final Boolean bestBlockchainHasChanged;

    public ProcessBlockResult(final Block block, final Long blockHeight, final Boolean isValid, final Boolean bestBlockchainHasChanged) {
        this.block = block;
        this.blockHeight = blockHeight;
        this.isValid = isValid;
        this.bestBlockchainHasChanged = bestBlockchainHasChanged;
    }
}
