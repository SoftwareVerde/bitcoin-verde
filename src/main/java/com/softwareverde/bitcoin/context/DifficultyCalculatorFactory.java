package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.context.DifficultyCalculatorContext;

public interface DifficultyCalculatorFactory {
    DifficultyCalculator newDifficultyCalculator(DifficultyCalculatorContext context);
}
