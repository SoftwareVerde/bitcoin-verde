package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.bip.Bip113;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.chain.time.MedianBlockTimeWithBlocks;
import com.softwareverde.bitcoin.server.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.timer.NanoTimer;

public class BlockHeaderValidator {
    protected final NetworkTime _networkTime;
    protected final MedianBlockTimeWithBlocks _medianBlockTime;
    protected final MysqlDatabaseConnection _databaseConnection;
    protected final DatabaseManagerCache _databaseManagerCache;

    protected Boolean _validateBlockHeader(final BlockHeader blockHeader, final Long blockHeight) {
        if (! blockHeader.isValid()) {
            Logger.log("Block header is invalid.");
            return false;
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
                Logger.log("Invalid block. Header invalid. BlockTime < MedianBlockTime. BlockTime: " + blockTime + " Minimum: " + minimumTimeInSeconds);
                return false;
            }
            if (blockTime > maximumNetworkTime) {
                Logger.log("Invalid block. Header invalid. BlockTime > NetworkTime. BlockTime: " + blockTime + " Maximum: " + maximumNetworkTime);
                return false;
            }
        }

        { // Validate block (calculated) difficulty...
            final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(_databaseConnection, _databaseManagerCache);
            final Difficulty calculatedRequiredDifficulty = difficultyCalculator.calculateRequiredDifficulty(blockHeader);
            if (calculatedRequiredDifficulty == null) {
                Logger.log("Unable to calculate required difficulty for block: " + blockHeader.getHash());
                return false;
            }

            final Boolean difficultyIsCorrect = calculatedRequiredDifficulty.equals(blockHeader.getDifficulty());
            if (! difficultyIsCorrect) {
                Logger.log("Invalid difficulty for block " + blockHeader.getHash() + ". Required: " + calculatedRequiredDifficulty.encode() + " Found: " + blockHeader.getDifficulty().encode());
                return false;
            }
        }

        validateBlockTimer.stop();
        // Logger.log("Validated Block Header in "+ (validateBlockTimer.getMillisecondsElapsed()) + "ms.");

        return true;
    }

    public BlockHeaderValidator(final MysqlDatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache, final NetworkTime networkTime, final MedianBlockTimeWithBlocks medianBlockTime) {
        _databaseConnection = databaseConnection;
        _databaseManagerCache = databaseManagerCache;
        _networkTime = networkTime;
        _medianBlockTime = medianBlockTime;
    }

    public Boolean validateBlockHeader(final BlockHeader blockHeader) {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(_databaseConnection, _databaseManagerCache);

        final BlockId blockId;
        final Long blockHeight;
        try {
            blockId = blockHeaderDatabaseManager.getBlockHeaderIdFromHash(blockHeader.getHash());
            blockHeight = blockHeaderDatabaseManager.getBlockHeightForBlockId(blockId);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return false;
        }

        return _validateBlockHeader(blockHeader, blockHeight);
    }

    public Boolean validateBlockHeader(final BlockHeader blockHeader, final Long blockHeight) {
        return _validateBlockHeader(blockHeader, blockHeight);
    }
}
