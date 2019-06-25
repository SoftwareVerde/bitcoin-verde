package com.softwareverde.bitcoin.transaction.script.opcode;

import org.junit.Assert;
import org.junit.Test;

public class OperationTests {
    @Test
    public void should_not_consider_negative_2147483648_as_within_integer_range() {
        // Setup
        final Long value = -2147483648L;

        // Action
        final Boolean isWithinIntegerRange = Operation.isWithinIntegerRange(value);

        // Assert
        Assert.assertFalse(isWithinIntegerRange);
    }
}
