package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.BlockHeaderContext;
import com.softwareverde.bitcoin.context.ChainWorkContext;
import com.softwareverde.bitcoin.context.DifficultyCalculatorContext;
import com.softwareverde.bitcoin.context.MedianBlockTimeContext;
import com.softwareverde.bitcoin.context.NetworkTimeContext;
import com.softwareverde.bitcoin.server.module.node.Blockchain;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.network.time.VolatileNetworkTime;
import com.softwareverde.util.Util;

public class BlockHeaderValidator {
    public static class BlockHeaderValidationResult {
        public static BlockHeaderValidationResult valid() {
            return new BlockHeaderValidationResult(true, null);
        }

        public static BlockHeaderValidationResult invalid(final String errorMessage) {
            return new BlockHeaderValidationResult(false, errorMessage);
        }

        public final Boolean isValid;
        public final String errorMessage;

        public BlockHeaderValidationResult(final Boolean isValid, final String errorMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
        }
    }

    protected final UpgradeSchedule _upgradeSchedule;
    protected final DifficultyCalculator _difficultyCalculator;
    protected final VolatileNetworkTime _networkTime;
    protected final Blockchain _blockchain;

    public BlockHeaderValidator(final UpgradeSchedule upgradeSchedule, final Blockchain blockchain, final VolatileNetworkTime networkTime, final DifficultyCalculator difficultyCalculator) {
        _upgradeSchedule = upgradeSchedule;
        _difficultyCalculator = difficultyCalculator;
        _blockchain = blockchain;
        _networkTime = networkTime;
    }

    public BlockHeaderValidationResult validateBlockHeader(final BlockHeader blockHeader, final Long blockHeight) {
        if (! blockHeader.isValid()) {
            return BlockHeaderValidationResult.invalid("Block header is invalid.");
        }

        { // Validate Block Timestamp...
            final Long blockTime = blockHeader.getTimestamp();
            final Long minimumTimeInSeconds;
            {
                if (_upgradeSchedule.shouldUseMedianBlockTimeForBlockTimestamp(blockHeight)) {
                    final Long previousBlockHeight = (blockHeight - 1L);
                    final MedianBlockTime medianBlockTime = _blockchain.getMedianBlockTime(previousBlockHeight);
                    minimumTimeInSeconds = medianBlockTime.getCurrentTimeInSeconds();
                }
                else {
                    minimumTimeInSeconds = 0L;
                }
            }

            final Long currentNetworkTimeInSeconds = _networkTime.getCurrentTimeInSeconds();
            final long secondsInTwoHours = 7200L;
            final long maximumNetworkTime = (currentNetworkTimeInSeconds + secondsInTwoHours);

            if (blockTime < minimumTimeInSeconds) {
                return BlockHeaderValidationResult.invalid("Invalid block. Header invalid. BlockTime < MedianBlockTime. BlockTime: " + blockTime + " Minimum: " + minimumTimeInSeconds);
            }
            if (blockTime > maximumNetworkTime) {
                return BlockHeaderValidationResult.invalid("Invalid block. Header invalid. BlockTime > NetworkTime. BlockTime: " + blockTime + " Maximum: " + maximumNetworkTime);
            }
        }

        { // Validate block (calculated) difficulty...
            final Difficulty calculatedRequiredDifficulty = _difficultyCalculator.calculateRequiredDifficulty(blockHeight);
            if (calculatedRequiredDifficulty == null) {
                return BlockHeaderValidationResult.invalid("Unable to calculate required difficulty for block: " + blockHeader.getHash());
            }

            final boolean difficultyIsCorrect = Util.areEqual(calculatedRequiredDifficulty, blockHeader.getDifficulty());
            if (! difficultyIsCorrect) {
                return BlockHeaderValidationResult.invalid("Invalid difficulty for block " + blockHeader.getHash() + " at height: " + blockHeight + ". Required: " + calculatedRequiredDifficulty.encode() + " Found: " + blockHeader.getDifficulty().encode());
            }
        }

        return BlockHeaderValidationResult.valid();
    }
}
