package com.softwareverde.bitcoin.server.socket.message;

import com.softwareverde.bitcoin.server.socket.message.networkaddress.NetworkAddress;
import com.softwareverde.bitcoin.server.socket.message.networkaddress.ip.Ipv4;
import com.softwareverde.bitcoin.server.socket.message.networkaddress.version.synchronize.SynchronizeVersionMessage;
import com.softwareverde.bitcoin.test.util.TestUtil;
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
            "0100 0000 0000 0000"+              // Node's Bitcoin Service Type
            "XXXX XXXX 0000 0000"+              // Message Timestamp
            "0100 0000 0000 0000 0000 0000 0000 0000 0000 FFFF C0A8 0101 208D"+ // Recipient's NetworkAddress
            "0100 0000 0000 0000 0000 0000 0000 0000 0000 FFFF 0000 0000 0000"+ // Sender's NetworkAddress
            "XXXX XXXX XXXX XXXX"+              // Nonce (NOTICE: BTC calls this "Node-Id")
            "152F 5665 7264 652D 4269 7463 6F69 6E3A XX2E XX2E XX2F"+           // Sub-Version (Length (Variable-Size-Integer) + "/Verde-Bitcoin:0.0.0/")
            "0000 0000"+
            "00"
        ;

        final NetworkAddress remoteNetworkAddress = new NetworkAddress();
        remoteNetworkAddress.setIp(Ipv4.parse("192.168.1.1"));
        remoteNetworkAddress.setPort(8333);
        remoteNetworkAddress.setServiceType(BitcoinServiceType.NETWORK);

        final SynchronizeVersionMessage synchronizeVersionMessage = new SynchronizeVersionMessage();
        synchronizeVersionMessage.setRemoteAddress(remoteNetworkAddress);

        // Action
        final byte[] serializedMessage = synchronizeVersionMessage.serializeAsLittleEndian();

        // Assert
        TestUtil.assertMatchesMaskedHexString(expectedHexStringMask, serializedMessage);
    }
}
