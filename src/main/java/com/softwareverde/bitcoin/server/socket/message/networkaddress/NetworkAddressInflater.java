package com.softwareverde.bitcoin.server.socket.message.networkaddress;

import com.softwareverde.bitcoin.server.socket.message.networkaddress.ip.Ipv4;
import com.softwareverde.bitcoin.server.socket.message.networkaddress.ip.Ipv6;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class NetworkAddressInflater {
    public NetworkAddress fromBytes(final byte[] bytes) {
        final NetworkAddress networkAddress = new NetworkAddress();

        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        networkAddress._timestamp = ((bytes.length > 26) ? byteArrayReader.readLong(4, Endian.LITTLE) : 0L);
        networkAddress._nodeFeatures.setFeatureFlags(byteArrayReader.readLong(8, Endian.LITTLE));

        {
            final byte[] ipv4CompatibilityBytes = BitcoinUtil.hexStringToByteArray("00000000000000000000FFFF");
            final byte[] nextBytes = byteArrayReader.peakBytes(12, Endian.BIG);
            final Boolean isIpv4Address = ByteUtil.areEqual(ipv4CompatibilityBytes, nextBytes);
            if (isIpv4Address) {
                byteArrayReader.skipBytes(12);
                networkAddress._ip = Ipv4.fromBytes(byteArrayReader.readBytes(4, Endian.BIG));
            }
            else {
                networkAddress._ip = Ipv6.fromBytes(byteArrayReader.readBytes(16, Endian.BIG));
            }
        }
        networkAddress._port = byteArrayReader.readInteger(2, Endian.BIG);

        return networkAddress;
    }
}
