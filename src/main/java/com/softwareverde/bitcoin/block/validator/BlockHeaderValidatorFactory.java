package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.chain.time.MedianBlockTimeWithBlocks;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.network.time.NetworkTime;

public class BlockHeaderValidatorFactory {
    protected final NetworkTime _networkTime;
    protected final MedianBlockTimeWithBlocks _medianBlockTime;

    public BlockHeaderValidatorFactory(final NetworkTime networkTime, final MedianBlockTimeWithBlocks medianBlockTimeWithBlocks) {
        _networkTime = networkTime;
        _medianBlockTime = medianBlockTimeWithBlocks;
    }

    public BlockHeaderValidator newBlockHeaderValidator(final DatabaseManager databaseManager) {
        return new BlockHeaderValidator(databaseManager, _networkTime, _medianBlockTime);
    }
}
