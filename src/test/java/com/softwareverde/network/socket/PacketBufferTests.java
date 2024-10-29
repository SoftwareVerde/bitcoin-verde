package com.softwareverde.network.socket;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

public class PacketBufferTests {
    private byte[] _hexStringToByteArray(final String hexString, final Integer extraByteCount) {
        final byte[] bytes = HexUtil.hexStringToByteArray(hexString.replaceAll(" ", ""));
        if (bytes == null) { return null; }

        final byte[] bytesWithExtra = new byte[bytes.length + extraByteCount];
        for (int i = 0; i < bytes.length; ++i) {
            bytesWithExtra[i] = bytes[i];
        }
        return bytesWithExtra;
    }

    @Test
    public void should_read_multiple_sets_of_appended_bytes_in_order() {
        // Setup
        final PacketBuffer packetBuffer = new PacketBuffer(BitcoinProtocolMessage.BINARY_PACKET_FORMAT);

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
        final PacketBuffer packetBuffer = new PacketBuffer(BitcoinProtocolMessage.BINARY_PACKET_FORMAT);

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
        for (int i = 0; i < 4; ++i) {
            final byte[] recycledBytes = packetBuffer.getRecycledBuffer();

            Assert.assertEquals(((i * 5) + 10), recycledBytes.length); // Ensure that the byte[] from above are being recycled (sizes in order: 0, 5, 10, 15)...

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

    @Test
    public void should_not_evict_uncorrupted_bytes() {
        // Setup
        final PacketBuffer packetBuffer = new PacketBuffer(BitcoinProtocolMessage.BINARY_PACKET_FORMAT);
        final ByteArray inventoryMessageBytes = ByteArray.fromHexString("E3E1F3E8696E7600000000000000000025000000166E09440101000000BA5F4826BC0C20BF0DAFAD3E4858D110F549040174A8EA924F3D4E409EB0D1EA");

        packetBuffer.appendBytes(inventoryMessageBytes.getBytes(), inventoryMessageBytes.getByteCount());

        // Action
        packetBuffer.evictCorruptedPackets();

        // Assert
        final int remainingByteCount = packetBuffer.getByteCount();
        final ByteArray bytes = ByteArray.wrap(packetBuffer.readBytes(remainingByteCount));
        Assert.assertEquals(inventoryMessageBytes, bytes);
    }

    @Test
    public void should_evict_corrupted_bytes_until_header_found() {
        // Setup
        final PacketBuffer packetBuffer = new PacketBuffer(BitcoinProtocolMessage.BINARY_PACKET_FORMAT);
        final ByteArray inventoryMessageBytes =             ByteArray.fromHexString(               "E3E1F3E8696E7600000000000000000025000000166E09440101000000BA5F4826BC0C20BF0DAFAD3E4858D110F549040174A8EA924F3D4E409EB0D1EA");
        final ByteArray inventoryMessageBytesWithGarbage =  ByteArray.fromHexString("E3E1E8F369" + "E3E1F3E8696E7600000000000000000025000000166E09440101000000BA5F4826BC0C20BF0DAFAD3E4858D110F549040174A8EA924F3D4E409EB0D1EA");

        packetBuffer.appendBytes(inventoryMessageBytesWithGarbage.getBytes(), inventoryMessageBytesWithGarbage.getByteCount());

        // Action
        packetBuffer.evictCorruptedPackets();

        // Assert
        final int remainingByteCount = packetBuffer.getByteCount();
        final ByteArray bytes = ByteArray.wrap(packetBuffer.readBytes(remainingByteCount));
        Assert.assertEquals(inventoryMessageBytes, bytes);
    }

    @Test
    public void should_evict_all_bytes_when_no_magic_number_is_found() {
        // Setup
        final PacketBuffer packetBuffer = new PacketBuffer(BitcoinProtocolMessage.BINARY_PACKET_FORMAT);
        final ByteArray expectedBytes = new MutableByteArray(0);
        final ByteArray inventoryMessageBytesWithoutMagicNumber =  ByteArray.fromHexString("00000000000000000025000000166E09440101000000BA5F4826BC0C20BF0DAFAD3E4858D110F549040174A8EA924F3D4E409EB0D1EA");

        packetBuffer.appendBytes(inventoryMessageBytesWithoutMagicNumber.getBytes(), inventoryMessageBytesWithoutMagicNumber.getByteCount());

        // Action
        packetBuffer.evictCorruptedPackets();

        // Assert
        final int remainingByteCount = packetBuffer.getByteCount();
        final ByteArray bytes = ByteArray.wrap(packetBuffer.readBytes(remainingByteCount));
        Assert.assertEquals(expectedBytes, bytes);
    }

    @Test
    public void should_evict_corrupted_bytes_until_header_found_when_multiple_messages_queued() {
        // Setup
        final PacketBuffer packetBuffer = new PacketBuffer(BitcoinProtocolMessage.BINARY_PACKET_FORMAT);
        final ByteArray inventoryMessageBytes =                 ByteArray.fromHexString(                    "E3E1F3E8696E7600000000000000000025000000166E09440101000000BA5F4826BC0C20BF0DAFAD3E4858D110F549040174A8EA924F3D4E409EB0D1EA");
        final ByteArray twoInventoryMessageBytesWithGarbage =   ByteArray.fromHexString("E3E1E8696E7E1F" +  "E3E1F3E8696E7600000000000000000025000000166E09440101000000BA5F4826BC0C20BF0DAFAD3E4858D110F549040174A8EA924F3D4E409EB0D1EA" + "E3E1F3E8696E7600000000000000000025000000166E09440101000000BA5F4826BC0C20BF0DAFAD3E4858D110F549040174A8EA924F3D4E409EB0D1EA");

        packetBuffer.appendBytes(twoInventoryMessageBytesWithGarbage.getBytes(), twoInventoryMessageBytesWithGarbage.getByteCount());

        // Action
        packetBuffer.evictCorruptedPackets();

        // Assert
        final ByteArray bytes0 = ByteArray.wrap(packetBuffer.readBytes(inventoryMessageBytes.getByteCount()));
        final ByteArray bytes1 = ByteArray.wrap(packetBuffer.readBytes(inventoryMessageBytes.getByteCount()));
        Assert.assertEquals(inventoryMessageBytes, bytes0);
        Assert.assertEquals(inventoryMessageBytes, bytes1);
    }

    @Test
    public void should_evict_corrupted_bytes_when_double_magic_is_provided() {
        // Setup
        final PacketBuffer packetBuffer = new PacketBuffer(BitcoinProtocolMessage.BINARY_PACKET_FORMAT);
        final int magicNumberCount = 4;
        final ByteArray inventoryMessageBytes =                     ByteArray.fromHexString(             "E3E1F3E8696E7600000000000000000025000000166E09440101000000BA5F4826BC0C20BF0DAFAD3E4858D110F549040174A8EA924F3D4E409EB0D1EA");
        final ByteArray twoInventoryMessageBytesWithDoubleMagic =   ByteArray.fromHexString("E3E1F3E8" + "E3E1F3E8696E7600000000000000000025000000166E09440101000000BA5F4826BC0C20BF0DAFAD3E4858D110F549040174A8EA924F3D4E409EB0D1EA" + "E3E1F3E8696E7600000000000000000025000000166E09440101000000BA5F4826BC0C20BF0DAFAD3E4858D110F549040174A8EA924F3D4E409EB0D1EA");

        packetBuffer.appendBytes(twoInventoryMessageBytesWithDoubleMagic.getBytes(), twoInventoryMessageBytesWithDoubleMagic.getByteCount());

        // Action
        packetBuffer.evictCorruptedPackets();

        // Assert
        final ByteArray bytes0 = ByteArray.wrap(packetBuffer.readBytes(inventoryMessageBytes.getByteCount() + magicNumberCount));
        final ByteArray bytes1 = ByteArray.wrap(packetBuffer.readBytes(inventoryMessageBytes.getByteCount()));
        Assert.assertEquals(ByteArray.fromHexString("E3E1F3E8"), ByteArray.wrap(bytes0.getBytes(0, magicNumberCount)));
        Assert.assertEquals(inventoryMessageBytes, ByteArray.wrap(bytes0.getBytes(magicNumberCount, (bytes0.getByteCount() - magicNumberCount))));
        Assert.assertEquals(inventoryMessageBytes, bytes1);
    }

    @Test
    public void should_consume_bytes_for_unsupported_message() {
        // Setup
        final PacketBuffer packetBuffer = new PacketBuffer(BitcoinProtocolMessage.BINARY_PACKET_FORMAT);

        final ByteArray inventoryMessageBytes = ByteArray.fromHexString("E3E1F3E8696E7600000000000000000025000000166E09440101000000BA5F4826BC0C20BF0DAFAD3E4858D110F549040174A8EA924F3D4E409EB0D1EA");
        final ByteArray zVersionMessageBytes = ByteArray.fromHexString("E3E1F3E87A76657273696F6E0000000071000000EFD064BA0EFE0000020003FD8D20FE010002000106FE020002000101FE030002000102FE040002000100FE050002000100FE060002000100FE070002000100FE080002000101FE0900020003FDF401FE0A00020005FEA0D21E00FE0B00020003FDF401FE0C00020005FEA0D21E00FE0D0002000101");

        packetBuffer.appendBytes(zVersionMessageBytes.getBytes(), zVersionMessageBytes.getByteCount());
        packetBuffer.appendBytes(inventoryMessageBytes.getBytes(), inventoryMessageBytes.getByteCount());

        final MutableList<BitcoinProtocolMessage> protocolMessages = new MutableArrayList<>();

        // Action
        packetBuffer.evictCorruptedPackets();

        while (packetBuffer.hasMessage()) {
            final BitcoinProtocolMessage protocolMessage = (BitcoinProtocolMessage) packetBuffer.popMessage();
            protocolMessages.add(protocolMessage);

            packetBuffer.evictCorruptedPackets();
        }

        // Assert
        Assert.assertEquals(2, protocolMessages.getCount());
        Assert.assertNull(protocolMessages.get(0));
        Assert.assertEquals(MessageType.INVENTORY, protocolMessages.get(1).getCommand());
    }

    @Test
    public void should_consume_bytes_for_unsupported_message_and_bad_payload() {
        // Setup
        final PacketBuffer packetBuffer = new PacketBuffer(BitcoinProtocolMessage.BINARY_PACKET_FORMAT);

        final ByteArray inventoryMessageBytes = ByteArray.fromHexString("E3E1F3E8696E7600000000000000000025000000166E09440101000000BA5F4826BC0C20BF0DAFAD3E4858D110F549040174A8EA924F3D4E409EB0D1EA");
        // NOTE: The payload size indicates 1 more byte than was actually provided.
        final ByteArray zVersionMessageBytes = ByteArray.fromHexString("E3E1F3E87A76657273696F6E0000000072000000EFD064BA0EFE0000020003FD8D20FE010002000106FE020002000101FE030002000102FE040002000100FE050002000100FE060002000100FE070002000100FE080002000101FE0900020003FDF401FE0A00020005FEA0D21E00FE0B00020003FDF401FE0C00020005FEA0D21E00FE0D0002000101");

        packetBuffer.appendBytes(zVersionMessageBytes.getBytes(), zVersionMessageBytes.getByteCount());
        packetBuffer.appendBytes(inventoryMessageBytes.getBytes(), inventoryMessageBytes.getByteCount()); // Message becomes mangled by the off-by-one in zVersion.
        packetBuffer.appendBytes(inventoryMessageBytes.getBytes(), inventoryMessageBytes.getByteCount());

        final MutableList<BitcoinProtocolMessage> protocolMessages = new MutableArrayList<>();

        // Action
        packetBuffer.evictCorruptedPackets();

        while (packetBuffer.hasMessage()) {
            final BitcoinProtocolMessage protocolMessage = (BitcoinProtocolMessage) packetBuffer.popMessage();
            protocolMessages.add(protocolMessage);

            packetBuffer.evictCorruptedPackets();
        }

        // Assert
        Assert.assertEquals(2, protocolMessages.getCount());
        Assert.assertNull(protocolMessages.get(0));
        Assert.assertEquals(MessageType.INVENTORY, protocolMessages.get(1).getCommand());
    }
}
