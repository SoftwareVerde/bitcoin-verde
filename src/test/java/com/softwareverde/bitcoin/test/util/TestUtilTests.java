package com.softwareverde.bitcoin.test.util;

import org.junit.Assert;
import org.junit.Test;

public class TestUtilTests {
    @Test
    public void mask_should_succeed_on_exact_match() {
        // Setup
        final String maskString = "0000";
        final String string = "0000";

        // Action
        TestUtil.assertMatchesMaskedHexString(maskString, string);
    }

    @Test
    public void mask_should_allow_spaces() {
        // Setup
        final String maskString = "0000 0000";
        final String string = "00000000";

        // Action
        TestUtil.assertMatchesMaskedHexString(maskString, string);
    }

    @Test
    public void mask_should_allow_masked_values() {
        // Setup
        final String maskString = "0000 0X0X 0000";
        final String string = "000001010000";

        // Action
        TestUtil.assertMatchesMaskedHexString(maskString, string);
    }

    @Test
    public void mask_should_fail_when_not_matching() {
        // Setup
        final String maskString = "0000 0X0X 0000";
        final String string = "000011110000";
        boolean exceptionThrown = false;

        // Action
        try {
            TestUtil.assertMatchesMaskedHexString(maskString, string);
        }
        catch (final AssertionError assertionError) {
            exceptionThrown = true;
        }

        // Assert
        Assert.assertTrue(exceptionThrown);
    }

    @Test
    public void mask_should_fail_when_lengths_do_not_match() {
        // Setup
        final String maskString = "0000 1111 0000";
        final String string = "00001111";
        boolean exceptionThrown = false;

        // Action
        try {
            TestUtil.assertMatchesMaskedHexString(maskString, string);
        }
        catch (final AssertionError assertionError) {
            exceptionThrown = true;
        }

        // Assert
        Assert.assertTrue(exceptionThrown);
    }
}
