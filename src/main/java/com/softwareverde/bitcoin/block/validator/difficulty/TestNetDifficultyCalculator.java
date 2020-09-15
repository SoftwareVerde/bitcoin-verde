package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.context.DifficultyCalculatorContext;
import com.softwareverde.logging.Logger;

public class TestNetDifficultyCalculator extends DifficultyCalculator {
    protected static final Long TWENTY_MINUTES = (20L * 60L);

    protected TestNetDifficultyCalculator(final DifficultyCalculatorContext blockchainContext, final MedianBlockHeaderSelector medianBlockHeaderSelector) {
        super(blockchainContext, medianBlockHeaderSelector);
    }

    public TestNetDifficultyCalculator(final DifficultyCalculatorContext blockchainContext) {
        super(blockchainContext);
    }

    @Override
    public Difficulty calculateRequiredDifficulty(final Long blockHeight) {
        if (blockHeight > 0) {
            final long secondsElapsed;
            {
                final BlockHeader previousBlockHeader = _context.getBlockHeader(blockHeight - 1L);
                final BlockHeader blockHeader = _context.getBlockHeader(blockHeight);
                secondsElapsed = (blockHeader.getTimestamp() - previousBlockHeader.getTimestamp());
            }

            if (secondsElapsed >= TWENTY_MINUTES) {
                return Difficulty.BASE_DIFFICULTY;
            }
            else {
                long ancestorBlockHeight = (blockHeight - 1L);
                while (true) {
                    if (ancestorBlockHeight < 1) {
                        return Difficulty.BASE_DIFFICULTY;
                    }

                    final BlockHeader previousBlockHeader = _context.getBlockHeader(ancestorBlockHeight - 1L);
                    final BlockHeader blockHeader = _context.getBlockHeader(ancestorBlockHeight);
                    final long ancestorSecondsElapsed = (blockHeader.getTimestamp() - previousBlockHeader.getTimestamp());
                    if (ancestorSecondsElapsed < TWENTY_MINUTES) {
                        return blockHeader.getDifficulty();
                    }

                    ancestorBlockHeight -= 1L;
                }
            }
        }

        return super.calculateRequiredDifficulty(blockHeight);
    }
}
