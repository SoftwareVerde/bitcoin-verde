package com.softwareverde.bitcoin.server.message.type.node.address;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.network.ip.Ipv4;
import com.softwareverde.network.ip.Ipv6;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class NodeIpAddressInflater {
    public BitcoinNodeIpAddress fromBytes(final byte[] bytes) {
        final BitcoinNodeIpAddress nodeIpAddress = new BitcoinNodeIpAddress();

        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        nodeIpAddress._timestamp = ((bytes.length > 26) ? byteArrayReader.readLong(4, Endian.LITTLE) : 0L);
        nodeIpAddress._nodeFeatures.setFeatureFlags(byteArrayReader.readLong(8, Endian.LITTLE));

        {
            final byte[] ipv4CompatibilityBytes = HexUtil.hexStringToByteArray("00000000000000000000FFFF");
            final byte[] nextBytes = byteArrayReader.peakBytes(12, Endian.BIG);
            final Boolean isIpv4Address = ByteUtil.areEqual(ipv4CompatibilityBytes, nextBytes);
            if (isIpv4Address) {
                byteArrayReader.skipBytes(12);
                nodeIpAddress.setIp(Ipv4.fromBytes(byteArrayReader.readBytes(4, Endian.BIG)));
            }
            else {
                nodeIpAddress.setIp(Ipv6.fromBytes(byteArrayReader.readBytes(16, Endian.BIG)));
            }
        }
        nodeIpAddress.setPort(byteArrayReader.readInteger(2, Endian.BIG));

        if (byteArrayReader.didOverflow()) { return null; }

        return nodeIpAddress;
    }
}
