package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.bip.Bip113;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MedianBlockTimeWithBlocks;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.Util;

public class BlockHeaderValidator {
    public static class BlockHeaderValidationResponse {
        public static BlockHeaderValidationResponse valid() {
            return new BlockHeaderValidationResponse(true, null);
        }

        public static BlockHeaderValidationResponse invalid(final String errorMessage) {
            return new BlockHeaderValidationResponse(false, errorMessage);
        }

        public final Boolean isValid;
        public final String errorMessage;

        public BlockHeaderValidationResponse(final Boolean isValid, final String errorMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
        }
    }

    protected final NetworkTime _networkTime;
    protected final MedianBlockTimeWithBlocks _medianBlockTime;
    protected final DatabaseManager _databaseManager;

    protected Boolean _validateBlockTimeForAlternateChain(final BlockHeader blockHeader) {
        try {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
            final BlockId headBlockId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
            final BlockId parentBlockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHeader.getPreviousBlockHash());

            if (Util.areEqual(headBlockId, parentBlockId)) { return false; }

            final MedianBlockTime forkedMedianBlockTime = blockHeaderDatabaseManager.calculateMedianBlockTimeStartingWithBlock(parentBlockId);
            final Long minimumTimeInSeconds = forkedMedianBlockTime.getCurrentTimeInSeconds();
            final Long blockTime = blockHeader.getTimestamp();

            return (blockTime >= minimumTimeInSeconds);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return false;
        }
    }

    protected BlockHeaderValidationResponse _validateBlockHeader(final BlockHeader blockHeader, final Long blockHeight, final BatchedBlockHeaders batchedBlockHeaders) {
        if (! blockHeader.isValid()) {
            return BlockHeaderValidationResponse.invalid("Block header is invalid.");
        }

        { // Validate Block Timestamp...
            final Long blockTime = blockHeader.getTimestamp();
            final Long minimumTimeInSeconds;
            {
                if (Bip113.isEnabled(blockHeight)) {
                    minimumTimeInSeconds = _medianBlockTime.getCurrentTimeInSeconds();
                }
                else {
                    minimumTimeInSeconds = 0L;
                }
            }
            final Long networkTime = _networkTime.getCurrentTimeInSeconds();
            final Long secondsInTwoHours = 7200L;
            final Long maximumNetworkTime = networkTime + secondsInTwoHours;

            if (blockTime < minimumTimeInSeconds) {
                final Boolean blockHeaderIsValidOnAlternateChain = _validateBlockTimeForAlternateChain(blockHeader);
                if (blockHeaderIsValidOnAlternateChain) {
                    Logger.info("Allowing header with timestamp from alternate chain.");
                }
                else {
                    return BlockHeaderValidationResponse.invalid("Invalid block. Header invalid. BlockTime < MedianBlockTime. BlockTime: " + blockTime + " Minimum: " + minimumTimeInSeconds);
                }
            }
            if (blockTime > maximumNetworkTime) {
                return BlockHeaderValidationResponse.invalid("Invalid block. Header invalid. BlockTime > NetworkTime. BlockTime: " + blockTime + " Maximum: " + maximumNetworkTime);
            }
        }

        { // Validate block (calculated) difficulty...
            final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(_databaseManager, batchedBlockHeaders);
            final Difficulty calculatedRequiredDifficulty = difficultyCalculator.calculateRequiredDifficulty(blockHeader);
            if (calculatedRequiredDifficulty == null) {
                return BlockHeaderValidationResponse.invalid("Unable to calculate required difficulty for block: " + blockHeader.getHash());
            }

            final boolean difficultyIsCorrect = calculatedRequiredDifficulty.equals(blockHeader.getDifficulty());
            if (! difficultyIsCorrect) {
                return BlockHeaderValidationResponse.invalid("Invalid difficulty for block " + blockHeader.getHash() + ". Required: " + calculatedRequiredDifficulty.encode() + " Found: " + blockHeader.getDifficulty().encode());
            }
        }

        return BlockHeaderValidationResponse.valid();
    }

    public BlockHeaderValidator(final DatabaseManager databaseManager, final NetworkTime networkTime, final MedianBlockTimeWithBlocks medianBlockTime) {
        _databaseManager = databaseManager;
        _networkTime = networkTime;
        _medianBlockTime = medianBlockTime;
    }

    public BlockHeaderValidationResponse validateBlockHeader(final BlockHeader blockHeader) {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

        final BlockId blockId;
        final Long blockHeight;
        try {
            blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHeader.getHash());
            blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return BlockHeaderValidationResponse.invalid("An internal error occurred.");
        }

        return _validateBlockHeader(blockHeader, blockHeight, null);
    }

    public BlockHeaderValidationResponse validateBlockHeaders(final BatchedBlockHeaders batchedBlockHeaders) {
        for (long blockHeight = batchedBlockHeaders.getStartingBlockHeight(); blockHeight < batchedBlockHeaders.getEndBlockHeight(); blockHeight += 1L) {
            final BlockHeader blockHeader = batchedBlockHeaders.getBlockHeader(blockHeight);
            final BlockHeaderValidationResponse blockHeaderValidationResponse = _validateBlockHeader(blockHeader, blockHeight, batchedBlockHeaders);
            if (! blockHeaderValidationResponse.isValid) { return blockHeaderValidationResponse; }
        }

        return BlockHeaderValidationResponse.valid();
    }

    public BlockHeaderValidationResponse validateBlockHeader(final BlockHeader blockHeader, final Long blockHeight) {
        return _validateBlockHeader(blockHeader, blockHeight, null);
    }
}
