package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;

public interface DifficultyCalculatorFactory {
    DifficultyCalculator newDifficultyCalculator(DifficultyCalculatorContext context);
}
