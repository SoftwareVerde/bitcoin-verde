package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;

public class ProcessBlockResult {
    public static ProcessBlockResult invalid(final Block block, final Long blockHeight, final String errorMessage) {
        return new ProcessBlockResult(block, blockHeight, false, false, null);
    }

    public static ProcessBlockResult valid(final Block block, final Long blockHeight, final Boolean bestBlockchainHasChanged) {
        return new ProcessBlockResult(block, blockHeight, true, bestBlockchainHasChanged, null);
    }

    public final Block block;
    public final Long blockHeight;
    public final Boolean isValid;
    public final Boolean bestBlockchainHasChanged;
    protected final String errorMessage;

    protected ProcessBlockResult(final Block block, final Long blockHeight, final Boolean isValid, final Boolean bestBlockchainHasChanged, final String errorMessage) {
        this.block = block;
        this.blockHeight = blockHeight;
        this.isValid = isValid;
        this.bestBlockchainHasChanged = bestBlockchainHasChanged;
        this.errorMessage = errorMessage;
    }
}
