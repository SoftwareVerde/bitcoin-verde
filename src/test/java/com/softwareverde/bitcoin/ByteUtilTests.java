package com.softwareverde.bitcoin;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import org.junit.Assert;
import org.junit.Test;

public class ByteUtilTests {
    @Test
    public void bytes_to_integer_tests() {
        Assert.assertEquals(0, ByteUtil.bytesToInteger(new byte[]{ (byte) 0x00              }));
        Assert.assertEquals(0, ByteUtil.bytesToInteger(new byte[]{ (byte) 0x00, (byte) 0x00 }));
        Assert.assertEquals(0, ByteUtil.bytesToInteger(new byte[]{                          }));

        Assert.assertEquals(1, ByteUtil.bytesToInteger(new byte[]{ (byte) 0x01              }));
        Assert.assertEquals(1, ByteUtil.bytesToInteger(new byte[]{ (byte) 0x00, (byte) 0x01 }));

        Assert.assertEquals(2, ByteUtil.bytesToInteger(new byte[]{ (byte) 0x02              }));
        Assert.assertEquals(2, ByteUtil.bytesToInteger(new byte[]{ (byte) 0x00, (byte) 0x02 }));

        Assert.assertEquals(255, ByteUtil.bytesToInteger(new byte[]{ (byte) 0xFF }));
        Assert.assertEquals(255, ByteUtil.bytesToInteger(new byte[]{ (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF }));
        Assert.assertEquals(255, ByteUtil.bytesToInteger(new byte[]{ (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF }));

        Assert.assertEquals(256, ByteUtil.bytesToInteger(new byte[]{ (byte) 0x01, (byte) 0x00 }));

        Assert.assertEquals(268435456, ByteUtil.bytesToInteger(new byte[]{ (byte) 0x10, (byte) 0x00, (byte) 0x00, (byte) 0x00 }));
    }

    @Test
    public void reverse_bytes_tests() {
        Assert.assertEquals((byte) 0b00000000, ByteUtil.reverseBits((byte) 0b00000000));
        Assert.assertEquals((byte) 0b10000000, ByteUtil.reverseBits((byte) 0b00000001));
        Assert.assertEquals((byte) 0b01000000, ByteUtil.reverseBits((byte) 0b00000010));
        Assert.assertEquals((byte) 0b00100000, ByteUtil.reverseBits((byte) 0b00000100));
        Assert.assertEquals((byte) 0b00010000, ByteUtil.reverseBits((byte) 0b00001000));
        Assert.assertEquals((byte) 0b00001000, ByteUtil.reverseBits((byte) 0b00010000));
        Assert.assertEquals((byte) 0b00000100, ByteUtil.reverseBits((byte) 0b00100000));
        Assert.assertEquals((byte) 0b00000010, ByteUtil.reverseBits((byte) 0b01000000));
        Assert.assertEquals((byte) 0b00000001, ByteUtil.reverseBits((byte) 0b10000000));
    }

    @Test
    public void should_ignore_destination_overflow_during_setBytes() {
        final MutableByteArray destination = new MutableByteArray(32);
        final Sha256Hash sha256Hash = Sha256Hash.fromHexString("000000000019D6689C085AE165831E934FF763AE46A2A6C172B3F1B60A8CE26F");

        ByteUtil.setBytes(destination, sha256Hash, 16);
        Assert.assertEquals(Sha256Hash.fromHexString("00000000000000000000000000000000000000000019D6689C085AE165831E93"), destination);
    }

    @Test
    public void lex_byte_compare() {
        {
            final ByteArray aByteArray = ByteArray.fromHexString("00000000");
            final ByteArray bByteArray = ByteArray.fromHexString("00000000");
            Assert.assertEquals(0, ByteUtil.compareByteArrayLexicographically(aByteArray, bByteArray));
            Assert.assertEquals(0, ByteUtil.compareByteArrayLexicographically(bByteArray, aByteArray));
        }

        {
            final ByteArray aByteArray = ByteArray.fromHexString("00000000");
            final ByteArray bByteArray = ByteArray.fromHexString("00000001");
            Assert.assertEquals(-1, ByteUtil.compareByteArrayLexicographically(aByteArray, bByteArray));
            Assert.assertEquals(1, ByteUtil.compareByteArrayLexicographically(bByteArray, aByteArray));
        }

        {
            final ByteArray aByteArray = ByteArray.fromHexString("00000001");
            final ByteArray bByteArray = ByteArray.fromHexString("00000000");
            Assert.assertEquals(1, ByteUtil.compareByteArrayLexicographically(aByteArray, bByteArray));
            Assert.assertEquals(-1, ByteUtil.compareByteArrayLexicographically(bByteArray, aByteArray));
        }

        {
            final ByteArray aByteArray = ByteArray.fromHexString("0000000000");
            final ByteArray bByteArray = ByteArray.fromHexString("00000001");
            Assert.assertEquals(-1, ByteUtil.compareByteArrayLexicographically(aByteArray, bByteArray));
            Assert.assertEquals(1, ByteUtil.compareByteArrayLexicographically(bByteArray, aByteArray));
        }

        {
            final ByteArray aByteArray = ByteArray.fromHexString("0000000001");
            final ByteArray bByteArray = ByteArray.fromHexString("00000000");
            Assert.assertEquals(1, ByteUtil.compareByteArrayLexicographically(aByteArray, bByteArray));
            Assert.assertEquals(-1, ByteUtil.compareByteArrayLexicographically(bByteArray, aByteArray));
        }
    }
}
