package com.softwareverde.bitcoin.server.socket.message;

import com.softwareverde.bitcoin.server.socket.message.networkaddress.NetworkAddress;
import com.softwareverde.bitcoin.server.socket.message.networkaddress.ip.Ipv4;
import com.softwareverde.bitcoin.server.socket.message.version.synchronize.SynchronizeVersionMessage;
import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import org.junit.Assert;
import org.junit.Test;

public class ProtocolMessageTests {
    @Test
    public void should_serialize_version_protocol_message() {
        // Setup
        final String expectedHexStringMask =
            "E3E1 F3E8"+                        // Magic Number
            "7665 7273 696F 6E00 0000 0000"+    // Version Command
            "6B00 0000"+                        // Payload Length
            "XXXX XXXX"+                        // Payload Checksum
            // Begin Payload
            "7F11 0100"+                        // Protocol Version
            "2100 0000 0000 0000"+              // Node's Bitcoin Service Type
            "XXXX XXXX 0000 0000"+              // Message Timestamp
            "0100 0000 0000 0000 0000 0000 0000 0000 0000 FFFF C0A8 0101 208D"+ // Recipient's NetworkAddress
            "0000 0000 0000 0000 0000 0000 0000 0000 0000 FFFF 0000 0000 0000"+ // Sender's NetworkAddress
            "XXXX XXXX XXXX XXXX"+              // Nonce (NOTICE: BTC calls this "Node-Id")
            "152F 5665 7264 652D 4269 7463 6F69 6E3A XX2E XX2E XX2F"+           // Sub-Version (Length (Variable-Size-Integer) + "/Verde-Bitcoin:0.0.0/")
            "0000 0000"+                        // Block Height
            "00"                                // Relay Enabled
        ;

        final NodeFeatures nodeFeatures = new NodeFeatures();
        nodeFeatures.enableFeatureFlag(NodeFeatures.Flags.BLOCKCHAIN_ENABLED);

        final NetworkAddress remoteNetworkAddress = new NetworkAddress();
        remoteNetworkAddress.setIp(Ipv4.parse("192.168.1.1"));
        remoteNetworkAddress.setPort(8333);
        remoteNetworkAddress.setNodeFeatures(nodeFeatures);

        final SynchronizeVersionMessage synchronizeVersionMessage = new SynchronizeVersionMessage();
        synchronizeVersionMessage.setRemoteAddress(remoteNetworkAddress);

        // Action
        final byte[] serializedMessage = synchronizeVersionMessage.getBytes();

        // Assert
        TestUtil.assertMatchesMaskedHexString(expectedHexStringMask, serializedMessage);
    }

    @Test
    public void should_deserialize_bitoin_xt_version_protocol_message() {
        // Setup
        final ProtocolMessageFactory protocolMessageFactory = new ProtocolMessageFactory();

        final String versionMessageHexString =
            "E3E1 F3E8"+                        // Magic Header
            "7665 7273 696F 6E00 0000 0000"+    // Command ("version")
            "7E00 0000"+                        // Payload Byte Count
            "419D 9392"+                        // Checksum
            // Begin Payload
            "7F11 0100"+                        // Protocol Version
            "3700 0000 0000 0000"+              // Node Features
            "E54E 7B5A 0000 0000"+              // Timestamp
            "0100 0000 0000 0000 0000 0000 0000 0000 0000 FFFF 1823 3C8A C64C"+ // NetworkAddress Remote (Without Timestamp)
            "3700 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000"+ // NetworkAddress Local (Without Timestamp)
            "8242 FB90 4E77 D660"+              // Nonce
            "282F 4269 7463 6F69 6E20 5854 3A30 2E31 312E 3048 284C 696E 7578 3B20 7838 365F 3634 3B20 4542 3829 2F"+   // User-Agent
            "1DE1 0700"+                        // Block Height
            "01"                                // Relay Enabled
        ;

        final byte[] versionMessage = BitcoinUtil.hexStringToByteArray(versionMessageHexString.replaceAll("\\s", ""));

        // Action
        final SynchronizeVersionMessage synchronizeVersionMessage = (SynchronizeVersionMessage) protocolMessageFactory.inflateMessage(versionMessage);

        // Assert
        Assert.assertNotNull(synchronizeVersionMessage);

        TestUtil.assertEqual(BitcoinUtil.hexStringToByteArray("E8F3E1E3"), synchronizeVersionMessage.getMagicNumber());
        Assert.assertEquals(ProtocolMessage.Command.SYNCHRONIZE_VERSION, synchronizeVersionMessage.getCommand());

        Assert.assertEquals(0x0000000000000037L, synchronizeVersionMessage.getNodeFeatures().getFeatureFlags().longValue());
        Assert.assertEquals(0x000000005A7B4EE5L, synchronizeVersionMessage.getTimestamp().longValue());

        final NetworkAddress remoteNetworkAddress = synchronizeVersionMessage.getRemoteNetworkAddress();
        TestUtil.assertEqual(new byte[]{ (byte) 0x18, (byte) 0x23, (byte) 0x3C, (byte) 0x8A }, remoteNetworkAddress.getIp().getBytes());
        Assert.assertEquals(50764, remoteNetworkAddress.getPort().intValue());
        Assert.assertEquals(0x0000000000000001L, remoteNetworkAddress.getNodeFeatures().getFeatureFlags().longValue());

        final NetworkAddress localNetworkAddress = synchronizeVersionMessage.getLocalNetworkAddress();
        TestUtil.assertEqual(new byte[]{ (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 }, localNetworkAddress.getIp().getBytes());
        Assert.assertEquals(0, localNetworkAddress.getPort().intValue());
        Assert.assertEquals(0x0000000000000037L, localNetworkAddress.getNodeFeatures().getFeatureFlags().longValue());

        Assert.assertEquals("/Bitcoin XT:0.11.0H(Linux; x86_64; EB8)/", synchronizeVersionMessage.getUserAgent());

        Assert.assertEquals(0x60D6774E90FB4282L, synchronizeVersionMessage.getNonce().longValue());
        Assert.assertEquals(0x000000000007E11DL, synchronizeVersionMessage.getCurrentBlockHeight().longValue());
        Assert.assertTrue(synchronizeVersionMessage.relayIsEnabled());
    }
}
