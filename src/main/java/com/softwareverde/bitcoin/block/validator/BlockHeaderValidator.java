package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.bip.Bip113;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MedianBlockTimeWithBlocks;
import com.softwareverde.bitcoin.server.module.node.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;

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
    protected final MysqlDatabaseConnection _databaseConnection;
    protected final DatabaseManagerCache _databaseManagerCache;

    protected Boolean _validateBlockTimeForAlternateChain(final BlockHeader blockHeader) {
        try {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(_databaseConnection, _databaseManagerCache);
            final BlockId headBlockId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
            final BlockId parentBlockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHeader.getPreviousBlockHash());

            if (Util.areEqual(headBlockId, parentBlockId)) { return false; }

            final MedianBlockTime forkedMedianBlockTime = blockHeaderDatabaseManager.calculateMedianBlockTimeStartingWithBlock(parentBlockId);
            final Long minimumTimeInSeconds = forkedMedianBlockTime.getCurrentTimeInSeconds();
            final Long blockTime = blockHeader.getTimestamp();

            return (blockTime >= minimumTimeInSeconds);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return false;
        }
    }

    protected BlockHeaderValidationResponse _validateBlockHeader(final BlockHeader blockHeader, final Long blockHeight) {
        if (! blockHeader.isValid()) {
            return BlockHeaderValidationResponse.invalid("Block header is invalid.");
        }

        final NanoTimer validateBlockTimer = new NanoTimer();
        validateBlockTimer.start();

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
                    Logger.log("INFO: Allowing header with timestamp from alternate chain.");
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
            final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(_databaseConnection, _databaseManagerCache);
            final Difficulty calculatedRequiredDifficulty = difficultyCalculator.calculateRequiredDifficulty(blockHeader);
            if (calculatedRequiredDifficulty == null) {
                return BlockHeaderValidationResponse.invalid("Unable to calculate required difficulty for block: " + blockHeader.getHash());
            }

            final Boolean difficultyIsCorrect = calculatedRequiredDifficulty.equals(blockHeader.getDifficulty());
            if (! difficultyIsCorrect) {
                return BlockHeaderValidationResponse.invalid("Invalid difficulty for block " + blockHeader.getHash() + ". Required: " + calculatedRequiredDifficulty.encode() + " Found: " + blockHeader.getDifficulty().encode());
            }
        }

        validateBlockTimer.stop();
        // Logger.log("Validated Block Header in "+ (validateBlockTimer.getMillisecondsElapsed()) + "ms.");

        return BlockHeaderValidationResponse.valid();
    }

    public BlockHeaderValidator(final MysqlDatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache, final NetworkTime networkTime, final MedianBlockTimeWithBlocks medianBlockTime) {
        _databaseConnection = databaseConnection;
        _databaseManagerCache = databaseManagerCache;
        _networkTime = networkTime;
        _medianBlockTime = medianBlockTime;
    }

    public BlockHeaderValidationResponse validateBlockHeader(final BlockHeader blockHeader) {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(_databaseConnection, _databaseManagerCache);

        final BlockId blockId;
        final Long blockHeight;
        try {
            blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHeader.getHash());
            blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return BlockHeaderValidationResponse.invalid("An internal error occurred.");
        }

        return _validateBlockHeader(blockHeader, blockHeight);
    }

    public BlockHeaderValidationResponse validateBlockHeader(final BlockHeader blockHeader, final Long blockHeight) {
        return _validateBlockHeader(blockHeader, blockHeight);
    }
}
