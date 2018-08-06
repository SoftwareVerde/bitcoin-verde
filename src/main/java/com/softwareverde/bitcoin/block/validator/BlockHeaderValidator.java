package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.bip.Bip113;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.timer.Timer;

public class BlockHeaderValidator {
    protected final NetworkTime _networkTime;
    protected final MedianBlockTime _medianBlockTime;
    protected final MysqlDatabaseConnection _databaseConnection;

    protected Boolean _validateBlockHeader(final BlockChainSegmentId blockChainSegmentId, final BlockHeader blockHeader, final Long blockHeight) {
        if (! blockHeader.isValid()) {
            Logger.log("Block header is invalid.");
            return false;
        }

        final Timer validateBlockTimer = new Timer();
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

            if (blockTime < minimumTimeInSeconds) { return false; }
            if (blockTime > maximumNetworkTime) { return false; }
        }

        { // Validate block (calculated) difficulty...
            final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(_databaseConnection);
            final Difficulty calculatedRequiredDifficulty = difficultyCalculator.calculateRequiredDifficulty(blockChainSegmentId, blockHeader);
            if (calculatedRequiredDifficulty == null) {
                Logger.log("Unable to calculate required difficulty for block: " + blockChainSegmentId + " " + blockHeader.getHash());
                return false;
            }

            final Boolean difficultyIsCorrect = calculatedRequiredDifficulty.equals(blockHeader.getDifficulty());
            if (!difficultyIsCorrect) {
                Logger.log("Invalid difficulty for block. Required: " + calculatedRequiredDifficulty.encode() + " Found: " + blockHeader.getDifficulty().encode());
                return false;
            }
        }

        validateBlockTimer.stop();
        Logger.log("Validated Block Header in "+ (validateBlockTimer.getMillisecondsElapsed()) + "ms.");

        return true;
    }

    public BlockHeaderValidator(final MysqlDatabaseConnection databaseConnection, final NetworkTime networkTime, final MedianBlockTime medianBlockTime) {
        _databaseConnection = databaseConnection;
        _networkTime = networkTime;
        _medianBlockTime = medianBlockTime;
    }

    public Boolean validateBlockHeader(final BlockChainSegmentId blockChainSegmentId, final BlockHeader blockHeader) {
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(_databaseConnection);

        final BlockId blockId;
        final Long blockHeight;
        try {
            blockId = blockDatabaseManager.getBlockIdFromHash(blockHeader.getHash());
            blockHeight = blockDatabaseManager.getBlockHeightForBlockId(blockId);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return false;
        }

        return _validateBlockHeader(blockChainSegmentId, blockHeader, blockHeight);
    }

    public Boolean validateBlockHeader(final BlockChainSegmentId blockChainSegmentId, final BlockHeader blockHeader, final Long blockHeight) {
        return _validateBlockHeader(blockChainSegmentId, blockHeader, blockHeight);
    }
}
