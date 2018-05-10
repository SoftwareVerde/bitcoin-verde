package com.softwareverde.network.socket;

import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

public class PacketBufferTests {
    private byte[] _hexStringToByteArray(final String hexString, final Integer extraByteCount) {
        final byte[] bytes = HexUtil.hexStringToByteArray(hexString.replaceAll(" ", ""));
        if (bytes == null) { return null; }

        final byte[] bytesWithExtra = new byte[bytes.length + extraByteCount];
        for (int i=0; i<bytes.length; ++i) {
            bytesWithExtra[i] = bytes[i];
        }
        return bytesWithExtra;
    }

    @Test
    public void should_read_multiple_sets_of_appended_bytes_in_order() {
        // Setup
        final PacketBuffer packetBuffer = new PacketBuffer();

        final byte[] magicNumber        = _hexStringToByteArray("E3E1 F3E8", 6);
        final byte[] command            = _hexStringToByteArray("7665 7273 696F 6E00 0000 0000", 0);
        final byte[] payloadByteCount   = _hexStringToByteArray("7E00 0000", 6);
        final byte[] checksum           = _hexStringToByteArray("419D 9392", 6);

        packetBuffer.appendBytes(magicNumber, 4);
        packetBuffer.appendBytes(command, 12);
        packetBuffer.appendBytes(payloadByteCount, 4);
        packetBuffer.appendBytes(checksum, 4);

        Assert.assertEquals(24, packetBuffer.getByteCount());

        // Action
        final byte[] bytes = packetBuffer.readBytes(24);

        // Assert
        TestUtil.assertMatchesMaskedHexString("E3E1 F3E8 7665 7273 696F 6E00 0000 0000 7E00 0000 419D 9392", bytes);
    }

    @Test
    public void should_be_recycle_byte_arrays_after_reading() {
        // Setup
        final PacketBuffer packetBuffer = new PacketBuffer();

        final byte[] magicNumber        = _hexStringToByteArray("FFFF FFFF FFFF FFFF FFFF", 0);
        final byte[] command            = _hexStringToByteArray("FFFF FFFF FFFF FFFF FFFF", 5);
        final byte[] payloadByteCount   = _hexStringToByteArray("FFFF FFFF FFFF FFFF FFFF", 10);
        final byte[] checksum           = _hexStringToByteArray("FFFF FFFF FFFF FFFF FFFF", 15);

        packetBuffer.appendBytes(magicNumber, 10);
        packetBuffer.appendBytes(command, 10);
        packetBuffer.appendBytes(payloadByteCount, 10);
        packetBuffer.appendBytes(checksum, 10);

        Assert.assertEquals(40, packetBuffer.getByteCount());

        // Action
        // First read the "easy" pattern of "FFFF"... After reading, this will recycle the internal byte arrays...
        final byte[] readBytes0 = packetBuffer.readBytes(40);

        int x = 0;
        for (int i=0; i<4; ++i) {
            final byte[] recycledBytes = packetBuffer.getRecycledBuffer();

            Assert.assertEquals(((i*5) + 10), recycledBytes.length); // Ensure that the byte[] from above are being recycled (sizes in order: 0, 5, 10, 15)...

            for (int j = 0; j < recycledBytes.length; ++j) {
                if (j < 10) {
                    // Byte Index is within the "usable" range, so populate them with request we want...
                    recycledBytes[j] = (byte) x;
                    x += 1;
                }
                else {
                    // Bytes Index is outside the "usable" range, so populate them with request we would recognize that we don't want...
                    recycledBytes[j] = 0x77;
                }
            }
            packetBuffer.appendBytes(recycledBytes, 10);
        }
        final byte[] readBytes1 = packetBuffer.readBytes(40);

        // Assert
        // The first byte[] should consist of the "easy "pattern...
        TestUtil.assertMatchesMaskedHexString("FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF", readBytes0);
        // The second byte[] should increment from 0x00 to 0x27, since we were telling the packetBuffer to only use the first 10 bytes of each buffer (i.e. packetBuffer.appendBytes(..., 10))
        TestUtil.assertMatchesMaskedHexString("0001 0203 0405 0607 0809 0A0B 0C0D 0E0F 1011 1213 1415 1617 1819 1A1B 1C1D 1E1F 2021 2223 2425 2627", readBytes1);
    }
}
