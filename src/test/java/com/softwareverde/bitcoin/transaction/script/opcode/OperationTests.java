package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.stack.Value;
import org.junit.Assert;
import org.junit.Test;

public class OperationTests {
    @Test
    public void should_not_consider_negative_9223372036854775808_as_within_integer_range() {
        // Setup
        final Value value = Value.fromInteger(Long.MIN_VALUE);

        // Action
        final Boolean isWithinIntegerRange = value.isWithinLongIntegerRange();

        // Assert
        Assert.assertFalse(isWithinIntegerRange);
    }
}
