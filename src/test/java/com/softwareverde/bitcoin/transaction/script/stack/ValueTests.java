package com.softwareverde.bitcoin.transaction.script.stack;

import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

public class ValueTests {

    @Test
    public void should_interpret_4_bytes_as_little_endian_integer() {
        // Setup
        final byte[] bytes = HexUtil.hexStringToByteArray("010000");
        final int expectedValue = 1;
        final Value value = Value.fromBytes(bytes);

        // Action
        final int receivedValue = value.asInteger();

        // Assert
        Assert.assertEquals(expectedValue, receivedValue);
    }

    @Test
    public void should_interpret_4_bytes_as_signed_little_endian_integer() {
        // Setup
        final byte[] bytes = HexUtil.hexStringToByteArray("010080");
        final int expectedValue = -1;
        final Value value = Value.fromBytes(bytes);

        // Action
        final int receivedValue = value.asInteger();

        // Assert
        Assert.assertEquals(expectedValue, receivedValue);
    }

    @Test
    public void should_interpret_1_bytes_as_signed_little_endian_integer() {
        // Setup
        final byte[] bytes = HexUtil.hexStringToByteArray("81");
        final int expectedValue = -1;
        final Value value = Value.fromBytes(bytes);

        // Action
        final int receivedValue = value.asInteger();

        // Assert
        Assert.assertEquals(expectedValue, receivedValue);
    }

    @Test
    public void should_interpret_2_bytes_as_signed_little_endian_integer() {
        // Setup
        final byte[] bytes = HexUtil.hexStringToByteArray("0180");
        final int expectedValue = -1;
        final Value value = Value.fromBytes(bytes);

        // Action
        final int receivedValue = value.asInteger();

        // Assert
        Assert.assertEquals(expectedValue, receivedValue);
    }

    @Test
    public void should_interpret_2_bytes_as_little_endian_integer() {
        // Setup
        final byte[] bytes = HexUtil.hexStringToByteArray("0100");
        final int expectedValue = 1;
        final Value value = Value.fromBytes(bytes);

        // Action
        final int receivedValue = value.asInteger();

        // Assert
        Assert.assertEquals(expectedValue, receivedValue);
    }

    @Test
    public void should_interpret_5_bytes_as_little_endian_integer() {
        // Setup
        final byte[] bytes = HexUtil.hexStringToByteArray("0100000000");
        final int expectedValue = 1;
        final Value value = Value.fromBytes(bytes);

        // Action
        final int receivedValue = value.asInteger();

        // Assert
        Assert.assertEquals(expectedValue, receivedValue);
    }

    @Test
    public void should_interpret_8_bytes_as_little_endian_long() {
        // Setup
        final byte[] bytes = HexUtil.hexStringToByteArray("0100000000000000");
        final long expectedValue = 1;
        final Value value = Value.fromBytes(bytes);

        // Action
        final long receivedValue = value.asLong();

        // Assert
        Assert.assertEquals(expectedValue, receivedValue);
    }

    @Test
    public void should_interpret_8_bytes_as_max_value_little_endian_long() {
        // Setup
        final byte[] bytes = HexUtil.hexStringToByteArray("FFFFFFFFFFFFFF7F");
        final long expectedValue = Long.MAX_VALUE;
        final Value value = Value.fromBytes(bytes);

        // Action
        final long receivedValue = value.asLong();

        // Assert
        Assert.assertEquals(expectedValue, receivedValue);
    }

    @Test
    public void should_interpret_1_byte_as_signed_little_endian_long() {
        // Setup
        final byte[] bytes = HexUtil.hexStringToByteArray("81");
        final long expectedValue = -1L;
        final Value value = Value.fromBytes(bytes);

        // Action
        final long receivedValue = value.asLong();

        // Assert
        Assert.assertEquals(expectedValue, receivedValue);
    }

    @Test
    public void should_interpret_8_bytes_as_signed_little_endian_long() {
        // Setup
        final byte[] bytes = HexUtil.hexStringToByteArray("FFFFFFFFFFFFFFFF");
        final long expectedValue = Long.MIN_VALUE + 1; // The Bitcoin integer encoding allows for negative zero, therefore, the min range is one less.
        final Value value = Value.fromBytes(bytes);

        // Action
        final long receivedValue = value.asLong();

        // Assert
        Assert.assertEquals(expectedValue, receivedValue);
    }

    @Test
    public void should_interpret_negative_zero_as_false() {
        // Setup
        final byte[] bytes = HexUtil.hexStringToByteArray("80");
        final boolean expectedValue = false;
        final Value value = Value.fromBytes(bytes);

        // Action
        final boolean receivedValue = value.asBoolean();

        // Assert
        Assert.assertEquals(expectedValue, receivedValue);
    }

    @Test
    public void should_maintain_integer_interpretation() {
        // Setup
        final Integer expectedIntegerValue = 7;
        final Value value = Value.fromInteger(expectedIntegerValue);

        // Action
        final Integer integerValue = value.asInteger();

        // Assert
        Assert.assertEquals(expectedIntegerValue, integerValue);
    }

    @Test
    public void should_maintain_boolean_interpretation() {
        // Setup
        final Boolean expectedValue = true;
        final Value value = Value.fromBoolean(expectedValue);

        // Action
        final Boolean booleanValue = value.asBoolean();

        // Assert
        Assert.assertEquals(expectedValue, booleanValue);
    }

    @Test
    public void should_convert_boolean_to_integer() {
        // Setup
        final Integer expectedIntegerValue = 1;
        final Value value = Value.fromBoolean(true);

        // Action
        final Integer integerValue = value.asInteger();

        // Assert
        Assert.assertEquals(expectedIntegerValue, integerValue);
    }
}
