package com.softwareverde.bitcoin.server.message;

import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.message.type.query.block.QueryBlocksMessage;
import com.softwareverde.bitcoin.server.message.type.query.header.RequestBlockHeadersMessage;
import com.softwareverde.bitcoin.server.message.type.version.synchronize.BitcoinSynchronizeVersionMessage;
import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.network.ip.Ipv4;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

public class ProtocolMessageTests {
    @Test
    public void should_serialize_version_protocol_message() {
        // Setup
        final String expectedHexStringMask =
            "E3E1 F3E8"+                        // Magic Number
            "7665 7273 696F 6E00 0000 0000"+    // Version MessageType
            "6B00 0000"+                        // Payload Length
            "XXXX XXXX"+                        // Payload Checksum
            // Begin Payload
            "7F11 0100"+                        // Protocol Version
            "2100 0000 0000 0000"+              // Node's Bitcoin Service Type
            "XXXX XXXX 0000 0000"+              // Message Timestamp
            "0100 0000 0000 0000 0000 0000 0000 0000 0000 FFFF C0A8 0101 208D"+ // Recipient's NodeIpAddress
            "0000 0000 0000 0000 0000 0000 0000 0000 0000 FFFF 0000 0000 0000"+ // Sender's NodeIpAddress
            "XXXX XXXX XXXX XXXX"+              // Nonce (NOTICE: BTC calls this "Node-DatabaseId")
            "152F 4269 7463 6F69 6E20 5665 7264 653A XX2E XX2E XX2F"+           // Sub-Version (Length (Variable-Size-Integer) + "/Bitcoin Verde:0.0.0/")
            "0000 0000"+                        // Block Height
            "00"                                // Relay Enabled
        ;

        final NodeFeatures remoteNodeFeatures = new NodeFeatures();
        remoteNodeFeatures.enableFeature(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);

        final BitcoinNodeIpAddress remoteNodeIpAddress = new BitcoinNodeIpAddress();
        remoteNodeIpAddress.setIp(Ipv4.parse("192.168.1.1"));
        remoteNodeIpAddress.setPort(8333);
        remoteNodeIpAddress.setNodeFeatures(remoteNodeFeatures);

        final NodeFeatures nodeFeatures = new NodeFeatures();
        nodeFeatures.enableFeature(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);
        nodeFeatures.enableFeature(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);

        final BitcoinSynchronizeVersionMessage synchronizeVersionMessage = new BitcoinSynchronizeVersionMessage();
        synchronizeVersionMessage.setRemoteAddress(remoteNodeIpAddress);
        synchronizeVersionMessage.setNodeFeatures(nodeFeatures);

        // Action
        final ByteArray serializedMessage = synchronizeVersionMessage.getBytes();

        // Assert
        TestUtil.assertMatchesMaskedHexString(expectedHexStringMask, serializedMessage.getBytes());
    }

    @Test
    public void should_deserialize_bitcoin_xt_version_protocol_message() {
        // Setup
        final BitcoinProtocolMessageFactory protocolMessageFactory = new BitcoinProtocolMessageFactory();

        final String versionMessageHexString =
            "E3E1 F3E8"+                        // Magic Header
            "7665 7273 696F 6E00 0000 0000"+    // MessageType ("version")
            "7E00 0000"+                        // Payload Byte Count
            "419D 9392"+                        // Checksum
            // Begin Payload
            "7F11 0100"+                        // Protocol Version
            "3700 0000 0000 0000"+              // Node Features
            "E54E 7B5A 0000 0000"+              // Timestamp
            "0100 0000 0000 0000 0000 0000 0000 0000 0000 FFFF 1823 3C8A C64C"+ // NodeIpAddress Remote (Without Timestamp)
            "3700 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000"+ // NodeIpAddress Local (Without Timestamp)
            "8242 FB90 4E77 D660"+              // Nonce
            "282F 4269 7463 6F69 6E20 5854 3A30 2E31 312E 3048 284C 696E 7578 3B20 7838 365F 3634 3B20 4542 3829 2F"+   // User-Agent
            "1DE1 0700"+                        // Block Height
            "01"                                // Relay Enabled
        ;

        final byte[] versionMessage = HexUtil.hexStringToByteArray(versionMessageHexString.replaceAll("\\s", ""));

        // Action
        final BitcoinSynchronizeVersionMessage synchronizeVersionMessage = (BitcoinSynchronizeVersionMessage) protocolMessageFactory.fromBytes(versionMessage);

        // Assert
        Assert.assertNotNull(synchronizeVersionMessage);

        TestUtil.assertEqual(HexUtil.hexStringToByteArray("E8F3E1E3"), synchronizeVersionMessage.getMagicNumber().getBytes());
        Assert.assertEquals(MessageType.SYNCHRONIZE_VERSION, synchronizeVersionMessage.getCommand());

        Assert.assertEquals(0x0000000000000037L, synchronizeVersionMessage.getNodeFeatures().getFeatureFlags().longValue());
        Assert.assertEquals(0x000000005A7B4EE5L, synchronizeVersionMessage.getTimestamp().longValue());

        final BitcoinNodeIpAddress remoteNodeIpAddress = synchronizeVersionMessage.getRemoteNodeIpAddress();
        TestUtil.assertEqual(new byte[]{ (byte) 0x18, (byte) 0x23, (byte) 0x3C, (byte) 0x8A }, remoteNodeIpAddress.getIp().getBytes().getBytes());
        Assert.assertEquals(50764, remoteNodeIpAddress.getPort().intValue());
        Assert.assertEquals(0x0000000000000001L, remoteNodeIpAddress.getNodeFeatures().getFeatureFlags().longValue());

        final BitcoinNodeIpAddress localNodeIpAddress = synchronizeVersionMessage.getLocalNodeIpAddress();
        TestUtil.assertEqual(new byte[]{ (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 }, localNodeIpAddress.getIp().getBytes().getBytes());
        Assert.assertEquals(0, localNodeIpAddress.getPort().intValue());
        Assert.assertEquals(0x0000000000000037L, localNodeIpAddress.getNodeFeatures().getFeatureFlags().longValue());

        Assert.assertEquals("/Bitcoin XT:0.11.0H(Linux; x86_64; EB8)/", synchronizeVersionMessage.getUserAgent());

        Assert.assertEquals(0x60D6774E90FB4282L, synchronizeVersionMessage.getNonce().longValue());
        Assert.assertEquals(0x000000000007E11DL, synchronizeVersionMessage.getCurrentBlockHeight().longValue());
        Assert.assertTrue(synchronizeVersionMessage.transactionRelayIsEnabled());
    }

    @Test
    public void should_deserialize_bitcoin_xt_getheaders_protocol_message() {
        // Setup
        final BitcoinProtocolMessageFactory protocolMessageFactory = new BitcoinProtocolMessageFactory();

        final String getHeadersMessageHexString =
            "E3E1 F3E8"+                        // Magic Header
            "6765 7468 6561 6465 7273 0000"+    // Command ("getheaders")
            "E503 0000"+                        // Payload Byte Count
            "A3E5 1C75"+                        // Checksum
                                                // Begin Payload
            "7F11 0100"+                        // Protocol Version
            "1E"+                               // Hash Count (30)
                                                // Begin Block Locator Hashes
            "13D6 88A9 98A2 6CE7 D049 6EB4 9A40 8621 F734 2BED 3E22 7E00 0000 0000 0000 0000"+
            "4DE3 422E F535 C199 2B31 CEFE 6F0F 2E78 D9A4 2FB1 3DCF 2603 0000 0000 0000 0000"+
            "0A28 2CE2 20C0 07E9 04B4 AAF9 BB44 E728 8E43 D4DE AB1F 9000 0000 0000 0000 0000"+
            "A727 88A2 C98C 88C7 D9BA BD38 C9DF 723E B861 CA73 6912 1201 0000 0000 0000 0000"+
            "53C5 C944 3BFC 887F AB9A 7D17 9AC5 11B5 9EED 7791 12D0 1102 0000 0000 0000 0000"+
            "6567 E373 8423 46F8 542E AF0F 4D82 943A 0C58 BDFD 972A 5000 0000 0000 0000 0000"+
            "17BF C90D BBD7 9AB8 1929 F8A0 5447 5A7C 7501 FA0C 964C 0702 0000 0000 0000 0000"+
            "A4E8 6B8F CC1D D08D 16EA 4156 D487 E744 6899 0C79 F63A DE01 0000 0000 0000 0000"+
            "09A6 1B19 06C5 3272 625D 7766 A015 A0A3 6B68 DA17 CAA7 5502 0000 0000 0000 0000"+
            "6C2F C33C A50B 1800 BEDE 9AE3 37CE 9AA8 1E96 D523 B066 AE02 0000 0000 0000 0000"+
            "E86D 95F7 72B8 31E3 F568 F131 9F07 408C 7FB3 4E4A 27AB 0200 0000 0000 0000 0000"+
            "33A7 D7D0 1BB9 FE23 DBAB 727B D036 26B7 41C9 DF65 F4C4 A501 0000 0000 0000 0000"+
            "18E0 524B EA77 B8F8 CC7E D5D3 0041 5867 41EA 7E29 0F13 BE02 0000 0000 0000 0000"+
            "63CD 6A5C 27F5 4471 823D FF73 408B 04EF 8CDA 9F9E F6F0 D401 0000 0000 0000 0000"+
            "8A60 B38A 06F4 D84B 5E8F 738C 0610 C137 47AC 99A7 C1E9 D902 0000 0000 0000 0000"+
            "2892 7FE7 AA9D 30E5 FF3A 8AC5 5C3D 3DEF 5DBB 6BFE 1D36 4F00 0000 0000 0000 0000"+
            "4A36 58CA 0D23 0FE4 0738 4F69 A56C A476 0A7C 856D 7543 F301 0000 0000 0000 0000"+
            "FEC9 B1D0 206B D14A 3B80 253E 3E29 9985 2B80 E183 0A5D CA01 0000 0000 0000 0000"+
            "A3AC 285D 14C4 717C F67C 4DE5 DF0D 9104 2555 0777 61E5 8200 0000 0000 0000 0000"+
            "B862 DCAC 52C4 E936 C1BA 13A9 5038 7CBA 5D59 9541 BC8B D300 0000 0000 0000 0000"+
            "4BED 3450 5033 B767 ED15 3C9B 0BD3 BD20 A06A A38A 6DBE 4E01 0000 0000 0000 0000"+
            "F725 E416 04A8 ED44 47CF 7675 94C1 5AA2 7E50 CD87 2F27 8603 0000 0000 0000 0000"+
            "E462 E014 2EF7 1069 27BD CF63 2351 2260 E1D2 EB7B 70D0 5404 0000 0000 0000 0000"+
            "4002 AB7D A22C E044 8C75 C50C 5BC8 95DD 7E60 801A C26C 7300 0000 0000 0000 0000"+
            "EEFB ACB7 8C66 81C6 46B6 D77B F167 07FC 827C 70D1 DEAC F400 0000 0000 0000 0000"+
            "AB7E B98F BDDC 06E1 5607 BB6F 73F6 36B5 3255 94F5 0464 5C03 0000 0000 0000 0000"+
            "8429 138E 5718 BF19 2B1F 895D 590B B738 B165 9814 6DA6 BD00 0000 0000 0000 0000"+
            "B039 8828 2B70 FCA0 654B E3F9 5289 1C5B AD89 3DE7 D9A8 4F07 0000 0000 0000 0000"+
            "7FEB C1B4 AA71 9748 8110 ABCD E125 A0D5 57BA 5AAE 43B9 A3B2 3200 0000 0000 0000"+
            "6FE2 8C0A B6F1 B372 C1A6 A246 AE63 F74F 931E 8365 E15A 089C 68D6 1900 0000 0000"+
            "0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000";

        final byte[] versionMessage = HexUtil.hexStringToByteArray(getHeadersMessageHexString.replaceAll("\\s", ""));

        // Action
        final RequestBlockHeadersMessage requestBlockHeadersMessage = (RequestBlockHeadersMessage) protocolMessageFactory.fromBytes(versionMessage);

        // Assert
        Assert.assertNotNull(requestBlockHeadersMessage);

        TestUtil.assertEqual(HexUtil.hexStringToByteArray("E8F3E1E3"), requestBlockHeadersMessage.getMagicNumber().getBytes());
        Assert.assertEquals(MessageType.REQUEST_BLOCK_HEADERS, requestBlockHeadersMessage.getCommand());

        final List<Sha256Hash> blockHeaderHashes = requestBlockHeadersMessage.getBlockHashes();
        Assert.assertEquals(30, blockHeaderHashes.getCount());

        TestUtil.assertEqual(HexUtil.hexStringToByteArray("0000000000000000007E223EED2B34F72186409AB46E49D0E76CA298A988D613"), blockHeaderHashes.get(0).getBytes());
        TestUtil.assertEqual(HexUtil.hexStringToByteArray("00000000000000000326CF3DB12FA4D9782E0F6FFECE312B99C135F52E42E34D"), blockHeaderHashes.get(1).getBytes());
        TestUtil.assertEqual(HexUtil.hexStringToByteArray("000000000019D6689C085AE165831E934FF763AE46A2A6C172B3F1B60A8CE26F"), blockHeaderHashes.get(29).getBytes());
        TestUtil.assertEqual(HexUtil.hexStringToByteArray("0000000000000000000000000000000000000000000000000000000000000000"), requestBlockHeadersMessage.getStopBeforeBlockHash().getBytes());
    }

    @Test
    public void should_deserialize_bitcoin_xt_getblocks_protocol_message() {
        // Setup
        final BitcoinProtocolMessageFactory protocolMessageFactory = new BitcoinProtocolMessageFactory();

        final String getHeadersMessageHexString =
            "E3E1 F3E8"+                        // Magic Header
            "6765 7462 6c6f 636b 7300 0000"+    // Command ("getblocks")
            "E503 0000"+                        // Payload Byte Count
            "A3E5 1C75"+                        // Checksum
            // Begin Payload
            "7F11 0100"+                        // Protocol Version
            "1E"+                               // Hash Count (30)
            // Begin Block Locator Hashes
            "13D6 88A9 98A2 6CE7 D049 6EB4 9A40 8621 F734 2BED 3E22 7E00 0000 0000 0000 0000"+
            "4DE3 422E F535 C199 2B31 CEFE 6F0F 2E78 D9A4 2FB1 3DCF 2603 0000 0000 0000 0000"+
            "0A28 2CE2 20C0 07E9 04B4 AAF9 BB44 E728 8E43 D4DE AB1F 9000 0000 0000 0000 0000"+
            "A727 88A2 C98C 88C7 D9BA BD38 C9DF 723E B861 CA73 6912 1201 0000 0000 0000 0000"+
            "53C5 C944 3BFC 887F AB9A 7D17 9AC5 11B5 9EED 7791 12D0 1102 0000 0000 0000 0000"+
            "6567 E373 8423 46F8 542E AF0F 4D82 943A 0C58 BDFD 972A 5000 0000 0000 0000 0000"+
            "17BF C90D BBD7 9AB8 1929 F8A0 5447 5A7C 7501 FA0C 964C 0702 0000 0000 0000 0000"+
            "A4E8 6B8F CC1D D08D 16EA 4156 D487 E744 6899 0C79 F63A DE01 0000 0000 0000 0000"+
            "09A6 1B19 06C5 3272 625D 7766 A015 A0A3 6B68 DA17 CAA7 5502 0000 0000 0000 0000"+
            "6C2F C33C A50B 1800 BEDE 9AE3 37CE 9AA8 1E96 D523 B066 AE02 0000 0000 0000 0000"+
            "E86D 95F7 72B8 31E3 F568 F131 9F07 408C 7FB3 4E4A 27AB 0200 0000 0000 0000 0000"+
            "33A7 D7D0 1BB9 FE23 DBAB 727B D036 26B7 41C9 DF65 F4C4 A501 0000 0000 0000 0000"+
            "18E0 524B EA77 B8F8 CC7E D5D3 0041 5867 41EA 7E29 0F13 BE02 0000 0000 0000 0000"+
            "63CD 6A5C 27F5 4471 823D FF73 408B 04EF 8CDA 9F9E F6F0 D401 0000 0000 0000 0000"+
            "8A60 B38A 06F4 D84B 5E8F 738C 0610 C137 47AC 99A7 C1E9 D902 0000 0000 0000 0000"+
            "2892 7FE7 AA9D 30E5 FF3A 8AC5 5C3D 3DEF 5DBB 6BFE 1D36 4F00 0000 0000 0000 0000"+
            "4A36 58CA 0D23 0FE4 0738 4F69 A56C A476 0A7C 856D 7543 F301 0000 0000 0000 0000"+
            "FEC9 B1D0 206B D14A 3B80 253E 3E29 9985 2B80 E183 0A5D CA01 0000 0000 0000 0000"+
            "A3AC 285D 14C4 717C F67C 4DE5 DF0D 9104 2555 0777 61E5 8200 0000 0000 0000 0000"+
            "B862 DCAC 52C4 E936 C1BA 13A9 5038 7CBA 5D59 9541 BC8B D300 0000 0000 0000 0000"+
            "4BED 3450 5033 B767 ED15 3C9B 0BD3 BD20 A06A A38A 6DBE 4E01 0000 0000 0000 0000"+
            "F725 E416 04A8 ED44 47CF 7675 94C1 5AA2 7E50 CD87 2F27 8603 0000 0000 0000 0000"+
            "E462 E014 2EF7 1069 27BD CF63 2351 2260 E1D2 EB7B 70D0 5404 0000 0000 0000 0000"+
            "4002 AB7D A22C E044 8C75 C50C 5BC8 95DD 7E60 801A C26C 7300 0000 0000 0000 0000"+
            "EEFB ACB7 8C66 81C6 46B6 D77B F167 07FC 827C 70D1 DEAC F400 0000 0000 0000 0000"+
            "AB7E B98F BDDC 06E1 5607 BB6F 73F6 36B5 3255 94F5 0464 5C03 0000 0000 0000 0000"+
            "8429 138E 5718 BF19 2B1F 895D 590B B738 B165 9814 6DA6 BD00 0000 0000 0000 0000"+
            "B039 8828 2B70 FCA0 654B E3F9 5289 1C5B AD89 3DE7 D9A8 4F07 0000 0000 0000 0000"+
            "7FEB C1B4 AA71 9748 8110 ABCD E125 A0D5 57BA 5AAE 43B9 A3B2 3200 0000 0000 0000"+
            "6FE2 8C0A B6F1 B372 C1A6 A246 AE63 F74F 931E 8365 E15A 089C 68D6 1900 0000 0000"+
            "0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000";

        final byte[] versionMessage = HexUtil.hexStringToByteArray(getHeadersMessageHexString.replaceAll("\\s", ""));

        // Action
        final QueryBlocksMessage queryBlocksMessage = (QueryBlocksMessage) protocolMessageFactory.fromBytes(versionMessage);

        // Assert
        Assert.assertNotNull(queryBlocksMessage);

        TestUtil.assertEqual(HexUtil.hexStringToByteArray("E8F3E1E3"), queryBlocksMessage.getMagicNumber().getBytes());
        Assert.assertEquals(MessageType.QUERY_BLOCKS, queryBlocksMessage.getCommand());

        final List<Sha256Hash> blockHeaderHashes = queryBlocksMessage.getBlockHashes();
        Assert.assertEquals(30, blockHeaderHashes.getCount());

        TestUtil.assertEqual(HexUtil.hexStringToByteArray("0000000000000000007E223EED2B34F72186409AB46E49D0E76CA298A988D613"), blockHeaderHashes.get(0).getBytes());
        TestUtil.assertEqual(HexUtil.hexStringToByteArray("00000000000000000326CF3DB12FA4D9782E0F6FFECE312B99C135F52E42E34D"), blockHeaderHashes.get(1).getBytes());
        TestUtil.assertEqual(HexUtil.hexStringToByteArray("000000000019D6689C085AE165831E934FF763AE46A2A6C172B3F1B60A8CE26F"), blockHeaderHashes.get(29).getBytes());
        TestUtil.assertEqual(HexUtil.hexStringToByteArray("0000000000000000000000000000000000000000000000000000000000000000"), queryBlocksMessage.getStopBeforeBlockHash().getBytes());

        Assert.assertEquals(MutableByteArray.wrap(versionMessage), queryBlocksMessage.getBytes());
    }
}
