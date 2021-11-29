package com.softwareverde.bitcoin.transaction.script.stack;

import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
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
        final Value value = Value.fromInteger(expectedIntegerValue.longValue());

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

    @Test
    public void should_convert_values_to_mpi_encoding() {
        {
            final long value = 1L;
            final byte[] bytes = Value._longToBytes(value);
            final Value mpiBytes = Value.minimallyEncodeBytes(MutableByteArray.wrap(bytes));
            TestUtil.assertEqual(HexUtil.hexStringToByteArray("01"), bytes);
            Assert.assertEquals(value, Value.fromBytes(bytes).asLong().longValue());
            Assert.assertEquals(Value.fromBytes(bytes), mpiBytes);
        }

        {
            final long value = 2L;
            final byte[] bytes = Value._longToBytes(value);
            final Value mpiBytes = Value.minimallyEncodeBytes(MutableByteArray.wrap(bytes));
            TestUtil.assertEqual(HexUtil.hexStringToByteArray("02"), bytes);
            Assert.assertEquals(value, Value.fromBytes(bytes).asLong().longValue());
            Assert.assertEquals(Value.fromBytes(bytes), mpiBytes);
        }

        {
            final long value = 15L;
            final byte[] bytes = Value._longToBytes(value);
            final Value mpiBytes = Value.minimallyEncodeBytes(MutableByteArray.wrap(bytes));
            TestUtil.assertEqual(HexUtil.hexStringToByteArray("0F"), bytes);
            Assert.assertEquals(value, Value.fromBytes(bytes).asLong().longValue());
            Assert.assertEquals(Value.fromBytes(bytes), mpiBytes);
        }

        {
            final long value = 16L;
            final byte[] bytes = Value._longToBytes(value);
            final Value mpiBytes = Value.minimallyEncodeBytes(MutableByteArray.wrap(bytes));
            TestUtil.assertEqual(HexUtil.hexStringToByteArray("10"), bytes);
            Assert.assertEquals(value, Value.fromBytes(bytes).asLong().longValue());
            Assert.assertEquals(Value.fromBytes(bytes), mpiBytes);
        }

        {
            final long value = 0x0100L;
            final byte[] bytes = Value._longToBytes(value);
            final Value mpiBytes = Value.minimallyEncodeBytes(MutableByteArray.wrap(bytes));
            TestUtil.assertEqual(HexUtil.hexStringToByteArray("0001"), bytes);
            Assert.assertEquals(value, Value.fromBytes(bytes).asLong().longValue());
            Assert.assertEquals(Value.fromBytes(bytes), mpiBytes);
        }

        {
            final long value = 0xFF;
            final byte[] bytes = Value._longToBytes(value);
            final Value mpiBytes = Value.minimallyEncodeBytes(MutableByteArray.wrap(bytes));
            TestUtil.assertEqual(HexUtil.hexStringToByteArray("FF00"), bytes);
            Assert.assertEquals(value, Value.fromBytes(bytes).asLong().longValue());
            Assert.assertEquals(Value.fromBytes(bytes), mpiBytes);
        }

        {
            final long value = 0xFF00;
            final byte[] bytes = Value._longToBytes(value);
            final Value mpiBytes = Value.minimallyEncodeBytes(MutableByteArray.wrap(bytes));
            TestUtil.assertEqual(HexUtil.hexStringToByteArray("00FF00"), bytes);
            Assert.assertEquals(value, Value.fromBytes(bytes).asLong().longValue());
            Assert.assertEquals(Value.fromBytes(bytes), mpiBytes);
        }

        {
            final long value = -0xFF00;
            final byte[] bytes = Value._longToBytes(value);
            final Value mpiBytes = Value.minimallyEncodeBytes(MutableByteArray.wrap(bytes));
            TestUtil.assertEqual(HexUtil.hexStringToByteArray("00FF80"), bytes);
            Assert.assertEquals(value, Value.fromBytes(bytes).asLong().longValue());
            Assert.assertEquals(Value.fromBytes(bytes), mpiBytes);
        }

        {
            final long value = -943534368L;
            final byte[] bytes = Value._longToBytes(value);
            final Value mpiBytes = Value.minimallyEncodeBytes(MutableByteArray.wrap(bytes));
            TestUtil.assertEqual(HexUtil.hexStringToByteArray("20313DB8"), bytes);
            Assert.assertEquals(value, Value.fromBytes(bytes).asLong().longValue());
            Assert.assertEquals(Value.fromBytes(bytes), mpiBytes);
        }

        {
            final long value = Integer.MAX_VALUE;
            final byte[] bytes = Value._longToBytes(value);
            final Value mpiBytes = Value.minimallyEncodeBytes(MutableByteArray.wrap(bytes));
            TestUtil.assertEqual(HexUtil.hexStringToByteArray("FFFFFF7F"), bytes);
            Assert.assertEquals(value, Value.fromBytes(bytes).asLong().longValue());
            Assert.assertEquals(Value.fromBytes(bytes), mpiBytes);
        }

        {
            final long value = Integer.MIN_VALUE;
            final byte[] bytes = Value._longToBytes(value);
            final Value mpiBytes = Value.minimallyEncodeBytes(MutableByteArray.wrap(bytes));
            TestUtil.assertEqual(HexUtil.hexStringToByteArray("00000080"), bytes);
            Assert.assertEquals(value, Value.fromBytes(bytes).asLong().longValue());
            Assert.assertEquals(Value.fromBytes(bytes), mpiBytes);
        }
    }

    @Test
    public void should_minimally_encode_value_that_is_not_minimally_encoded() {
        // Setup
        final ByteArray notMinimallyEncodedByteArray = ByteArray.fromHexString("ABCDEF4280");
        final ByteArray expectedEncoding = ByteArray.fromHexString("ABCDEFC2");

        // Action
        final ByteArray encodedValue = Value.minimallyEncodeBytes(notMinimallyEncodedByteArray);

        // Assert
        Assert.assertEquals(expectedEncoding, encodedValue);
    }

    @Test
    public void should_inflate_64bit_value_from_long() {
        // Setup
        final Long expectedValue = (Long.MIN_VALUE + 1L);

        // Action
        final Value value = Value.fromInteger(expectedValue);
        final Long valueLong = value.asLong();

        // Assert
        Assert.assertEquals(valueLong, expectedValue);
    }

    @Test
    public void should_inflate_32bit_value_from_long() {
        // Setup
        final Integer expectedValue = (Integer.MIN_VALUE + 1);

        // Action
        final Value value = Value.fromInteger(expectedValue);
        final Integer valueLong = value.asInteger();

        // Assert
        Assert.assertEquals(valueLong, expectedValue);
    }

    @Test
    public void minimally_encode_long_value() {
        // Setup
        final Value value = Value.fromBytes(ByteArray.fromHexString("668E97BAF243"));
        final Value fromIntegerValue = Value.fromInteger(value.asLong());

        // Action
        final Boolean isMinimallyEncoded = value.isMinimallyEncoded();
        final Boolean isMinimallyEncoded2 = fromIntegerValue.isMinimallyEncoded();

        // Assert
        Assert.assertTrue(isMinimallyEncoded);
        Assert.assertTrue(isMinimallyEncoded2);
    }
}
