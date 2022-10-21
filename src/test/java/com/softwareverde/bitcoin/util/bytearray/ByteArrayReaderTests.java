package com.softwareverde.bitcoin.util.bytearray;

import com.softwareverde.bitcoin.test.UnitTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ByteArrayReaderTests extends UnitTest {
    @Before
    public void before() throws Exception {
        super.before();
    }

    @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_enforce_minimally_encoded_compact_variable_length_integer() throws Exception {
        // (Below ranges are inclusive)
        // 0-252: 1 byte                        (>   0x00000000000000FD)
        // 253-65535: prefix + 2 bytes          (>   0x0000000000010000)
        // 65536-4294967295: prefix + 4 bytes   (>   0x0000000100000000)
        // 4294967296+: prefix + 8 bytes        (> 0x010000000000000000)
        //                                             FEDCBA9876543210

        // 1 Byte
        Assert.assertTrue((new CompactVariableLengthInteger(0, 1)).isCanonical());
        Assert.assertTrue((new CompactVariableLengthInteger(252, 1)).isCanonical());

        Assert.assertFalse((new CompactVariableLengthInteger(0, 3)).isCanonical());
        Assert.assertFalse((new CompactVariableLengthInteger(0, 5)).isCanonical());
        Assert.assertFalse((new CompactVariableLengthInteger(0, 9)).isCanonical());

        // 3 Bytes
        Assert.assertTrue((new CompactVariableLengthInteger(253, 3)).isCanonical());
        Assert.assertTrue((new CompactVariableLengthInteger(254, 3)).isCanonical());
        Assert.assertTrue((new CompactVariableLengthInteger(255, 3)).isCanonical());
        Assert.assertTrue((new CompactVariableLengthInteger(256, 3)).isCanonical());
        Assert.assertTrue((new CompactVariableLengthInteger(257, 3)).isCanonical());
        Assert.assertTrue((new CompactVariableLengthInteger(65535, 3)).isCanonical());


        Assert.assertFalse((new CompactVariableLengthInteger(253, 5)).isCanonical());
        Assert.assertFalse((new CompactVariableLengthInteger(253, 9)).isCanonical());
        Assert.assertFalse((new CompactVariableLengthInteger(254, 5)).isCanonical());
        Assert.assertFalse((new CompactVariableLengthInteger(254, 9)).isCanonical());
        Assert.assertFalse((new CompactVariableLengthInteger(255, 5)).isCanonical());
        Assert.assertFalse((new CompactVariableLengthInteger(255, 9)).isCanonical());
        Assert.assertFalse((new CompactVariableLengthInteger(256, 5)).isCanonical());
        Assert.assertFalse((new CompactVariableLengthInteger(256, 9)).isCanonical());
        Assert.assertFalse((new CompactVariableLengthInteger(257, 5)).isCanonical());
        Assert.assertFalse((new CompactVariableLengthInteger(257, 9)).isCanonical());
        Assert.assertFalse((new CompactVariableLengthInteger(65535, 5)).isCanonical());
        Assert.assertFalse((new CompactVariableLengthInteger(65535, 9)).isCanonical());

        // 5 Bytes
        Assert.assertTrue((new CompactVariableLengthInteger(65536, 5)).isCanonical());
        Assert.assertTrue((new CompactVariableLengthInteger(65537, 5)).isCanonical());
        Assert.assertTrue((new CompactVariableLengthInteger(4294967295L, 5)).isCanonical());

        Assert.assertFalse((new CompactVariableLengthInteger(65536, 9)).isCanonical());
        Assert.assertFalse((new CompactVariableLengthInteger(65537, 9)).isCanonical());
        Assert.assertFalse((new CompactVariableLengthInteger(4294967295L, 9)).isCanonical());

        // 9 Bytes
        Assert.assertTrue((new CompactVariableLengthInteger(4294967296L, 9)).isCanonical());
        Assert.assertTrue((new CompactVariableLengthInteger(Long.MAX_VALUE, 9)).isCanonical());
    }
}
