package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;

public interface DifficultyCalculatorContext extends BlockHeaderContext, ChainWorkContext, MedianBlockTimeContext, UpgradeScheduleContext, AsertReferenceBlockContext {
    DifficultyCalculator newDifficultyCalculator();
}
