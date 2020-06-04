package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.bip.Bip113;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.BlockHeaderContext;
import com.softwareverde.bitcoin.context.ChainWorkContext;
import com.softwareverde.bitcoin.context.MedianBlockTimeContext;
import com.softwareverde.bitcoin.context.NetworkTimeContext;
import com.softwareverde.network.time.NetworkTime;

public class BlockHeaderValidator {
    public interface Context extends BlockHeaderContext, ChainWorkContext, MedianBlockTimeContext, NetworkTimeContext { }

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

    protected final Context _context;

    public BlockHeaderValidator(final Context context) {
        _context = context;
    }

    public BlockHeaderValidationResult validateBlockHeader(final BlockHeader blockHeader, final Long blockHeight) {
        if (! blockHeader.isValid()) {
            return BlockHeaderValidationResult.invalid("Block header is invalid.");
        }

        { // Validate Block Timestamp...
            final Long blockTime = blockHeader.getTimestamp();
            final Long minimumTimeInSeconds;
            {
                if (Bip113.isEnabled(blockHeight)) {
                    final Long previousBlockHeight = (blockHeight - 1L);
                    final MedianBlockTime medianBlockTime = _context.getMedianBlockTime(previousBlockHeight);
                    minimumTimeInSeconds = medianBlockTime.getCurrentTimeInSeconds();
                }
                else {
                    minimumTimeInSeconds = 0L;
                }
            }
            final NetworkTime networkTime = _context.getNetworkTime();
            final Long currentNetworkTimeInSeconds = networkTime.getCurrentTimeInSeconds();
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
            final DifficultyCalculator<?> difficultyCalculator = new DifficultyCalculator<>(_context);
            final Difficulty calculatedRequiredDifficulty = difficultyCalculator.calculateRequiredDifficulty(blockHeight);
            if (calculatedRequiredDifficulty == null) {
                return BlockHeaderValidationResult.invalid("Unable to calculate required difficulty for block: " + blockHeader.getHash());
            }

            final boolean difficultyIsCorrect = calculatedRequiredDifficulty.equals(blockHeader.getDifficulty());
            if (! difficultyIsCorrect) {
                return BlockHeaderValidationResult.invalid("Invalid difficulty for block " + blockHeader.getHash() + ". Required: " + calculatedRequiredDifficulty.encode() + " Found: " + blockHeader.getDifficulty().encode());
            }
        }

        return BlockHeaderValidationResult.valid();
    }
}
