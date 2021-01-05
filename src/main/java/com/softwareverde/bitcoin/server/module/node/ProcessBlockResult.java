package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;

public class ProcessBlockResult {
    public static ProcessBlockResult invalid(final Block block, final Long blockHeight, final String errorMessage) {
        return new ProcessBlockResult(block, blockHeight, false, false, null, null);
    }

    public static ProcessBlockResult valid(final Block block, final Long blockHeight, final Boolean bestBlockchainHasChanged, final Boolean wasAlreadyProcessed) {
        return new ProcessBlockResult(block, blockHeight, true, bestBlockchainHasChanged, null, wasAlreadyProcessed);
    }

    public final Block block;
    public final Long blockHeight;
    public final Boolean isValid;
    public final Boolean bestBlockchainHasChanged;
    public final Boolean wasAlreadyProcessed; // Is null if the block was not valid.
    protected final String errorMessage;

    protected ProcessBlockResult(final Block block, final Long blockHeight, final Boolean isValid, final Boolean bestBlockchainHasChanged, final String errorMessage, final Boolean wasAlreadyProcessed) {
        this.block = block;
        this.blockHeight = blockHeight;
        this.isValid = isValid;
        this.bestBlockchainHasChanged = bestBlockchainHasChanged;
        this.errorMessage = errorMessage;
        this.wasAlreadyProcessed = wasAlreadyProcessed;
    }
}
