package com.softwareverde.bitcoin.server.socket;

import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import org.junit.Assert;
import org.junit.Test;

public class BitcoinProtocolMessagePacketBufferTests {
    private byte[] _hexStringToByteArray(final String hexString, final Integer extraByteCount) {
        final byte[] bytes = BitcoinUtil.hexStringToByteArray(hexString.replaceAll(" ", ""));
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
        final BitcoinProtocolMessagePacketBuffer bitcoinProtocolMessagePacketBuffer = new BitcoinProtocolMessagePacketBuffer();

        final byte[] magicNumber        = _hexStringToByteArray("E3E1 F3E8", 6);
        final byte[] command            = _hexStringToByteArray("7665 7273 696F 6E00 0000 0000", 0);
        final byte[] payloadByteCount   = _hexStringToByteArray("7E00 0000", 6);
        final byte[] checksum           = _hexStringToByteArray("419D 9392", 6);

        bitcoinProtocolMessagePacketBuffer.appendBytes(magicNumber, 4);
        bitcoinProtocolMessagePacketBuffer.appendBytes(command, 12);
        bitcoinProtocolMessagePacketBuffer.appendBytes(payloadByteCount, 4);
        bitcoinProtocolMessagePacketBuffer.appendBytes(checksum, 4);

        Assert.assertEquals(24, bitcoinProtocolMessagePacketBuffer.getByteCount());

        // Action
        final byte[] bytes = bitcoinProtocolMessagePacketBuffer.readBytes(24);

        // Assert
        TestUtil.assertMatchesMaskedHexString("E3E1 F3E8 7665 7273 696F 6E00 0000 0000 7E00 0000 419D 9392", bytes);
    }

    @Test
    public void should_be_recycle_byte_arrays_after_reading() {
        // Setup
        final BitcoinProtocolMessagePacketBuffer bitcoinProtocolMessagePacketBuffer = new BitcoinProtocolMessagePacketBuffer();

        final byte[] magicNumber        = _hexStringToByteArray("FFFF FFFF FFFF FFFF FFFF", 0);
        final byte[] command            = _hexStringToByteArray("FFFF FFFF FFFF FFFF FFFF", 5);
        final byte[] payloadByteCount   = _hexStringToByteArray("FFFF FFFF FFFF FFFF FFFF", 10);
        final byte[] checksum           = _hexStringToByteArray("FFFF FFFF FFFF FFFF FFFF", 15);

        bitcoinProtocolMessagePacketBuffer.appendBytes(magicNumber, 10);
        bitcoinProtocolMessagePacketBuffer.appendBytes(command, 10);
        bitcoinProtocolMessagePacketBuffer.appendBytes(payloadByteCount, 10);
        bitcoinProtocolMessagePacketBuffer.appendBytes(checksum, 10);

        Assert.assertEquals(40, bitcoinProtocolMessagePacketBuffer.getByteCount());

        // Action
        // First read the "easy" pattern of "FFFF"... After reading, this will recycle the internal byte arrays...
        final byte[] readBytes0 = bitcoinProtocolMessagePacketBuffer.readBytes(40);

        int x = 0;
        for (int i=0; i<4; ++i) {
            final byte[] recycledBytes = bitcoinProtocolMessagePacketBuffer.getRecycledBuffer();

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
            bitcoinProtocolMessagePacketBuffer.appendBytes(recycledBytes, 10);
        }
        final byte[] readBytes1 = bitcoinProtocolMessagePacketBuffer.readBytes(40);

        // Assert
        // The first byte[] should consist of the "easy "pattern...
        TestUtil.assertMatchesMaskedHexString("FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF", readBytes0);
        // The second byte[] should increment from 0x00 to 0x27, since we were telling the packetBuffer to only use the first 10 bytes of each buffer (i.e. packetBuffer.appendBytes(..., 10))
        TestUtil.assertMatchesMaskedHexString("0001 0203 0405 0607 0809 0A0B 0C0D 0E0F 1011 1213 1415 1617 1819 1A1B 1C1D 1E1F 2021 2223 2425 2627", readBytes1);
    }
}
