package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;

public interface DifficultyCalculatorContext extends BlockHeaderContext, ChainWorkContext, MedianBlockTimeContext, AsertReferenceBlockContext {
    DifficultyCalculator newDifficultyCalculator();
}
