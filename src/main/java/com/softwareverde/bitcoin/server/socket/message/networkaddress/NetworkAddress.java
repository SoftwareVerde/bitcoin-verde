package com.softwareverde.bitcoin.server.socket.message.networkaddress;

import com.softwareverde.bitcoin.server.socket.message.NodeFeatures;
import com.softwareverde.bitcoin.server.socket.message.networkaddress.ip.Ip;
import com.softwareverde.bitcoin.server.socket.message.networkaddress.ip.Ipv4;
import com.softwareverde.bitcoin.server.socket.message.networkaddress.ip.Ipv6;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.bitcoin.util.ByteUtil;

public class NetworkAddress {
    private static class ByteData {
        public final byte[] timestamp = new byte[4];
        public final byte[] nodeFeatureFlags = new byte[8];
        public final byte[] ip = new byte[16];
        public final byte[] port = new byte[2];
    }

    public static NetworkAddress fromBytes(final byte[] bytes) {
        final NetworkAddress networkAddress = new NetworkAddress();

        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        if (bytes.length > 26) {
            networkAddress._timestamp = byteArrayReader.readLong(4, Endian.LITTLE);
        }
        else {
            networkAddress._timestamp = 0L;
        }

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

    private Long _timestamp;
    private final NodeFeatures _nodeFeatures;
    private Ip _ip;
    private Integer _port;

    protected ByteData _createByteData() {
        final ByteData byteData = new ByteData();
        ByteUtil.setBytes(byteData.timestamp, ByteUtil.longToBytes(_timestamp));
        ByteUtil.setBytes(byteData.nodeFeatureFlags, ByteUtil.longToBytes(_nodeFeatures.getFeatureFlags()));

        {
            final byte[] ipBytes = _ip.getBytes();
            if (ipBytes.length < 16) {
                final byte[] paddedBytes = Ipv6.createIpv4CompatibleIpv6(new Ipv4(ipBytes)).getBytes();
                ByteUtil.setBytes(byteData.ip, paddedBytes);
            }
            else {
                ByteUtil.setBytes(byteData.ip, ipBytes);
            }
        }

        {
            final int portIntValue = (_port == null ? 0x0000 : _port);
            byteData.port[0] = (byte) (portIntValue >>> 8);
            byteData.port[1] = (byte) portIntValue;
        }

        return byteData;
    }

    public NetworkAddress() {
        _timestamp = (System.currentTimeMillis() / 1000L);
        _nodeFeatures = new NodeFeatures();
        _ip = new Ipv4();
        _port = 0x0000;
    }


    public void setNodeFeatures(final NodeFeatures nodeFeatures) {
        _nodeFeatures.setFeaturesFlags(nodeFeatures);
    }
    public NodeFeatures getNodeFeatures() { return _nodeFeatures; }

    public void setIp(final Ip ip) {
        _ip = ( (ip != null) ? ip.duplicate() : new Ipv4());
    }

    public Ip getIp() {
        return _ip.duplicate();
    }

    public void setPort(final Integer port) { _port = port; }
    public Integer getPort() { return _port; }

    public byte[] getBytesWithoutTimestamp() {
        final ByteData byteData = _createByteData();
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        byteArrayBuilder.appendBytes(byteData.nodeFeatureFlags, Endian.LITTLE);
        byteArrayBuilder.appendBytes(byteData.ip, Endian.BIG);
        byteArrayBuilder.appendBytes(byteData.port, Endian.BIG);

        return byteArrayBuilder.build();
    }

    public byte[] getBytesWithTimestamp() {
        final ByteData byteData = _createByteData();
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        byteArrayBuilder.appendBytes(byteData.timestamp, Endian.LITTLE);
        byteArrayBuilder.appendBytes(byteData.nodeFeatureFlags, Endian.LITTLE);
        byteArrayBuilder.appendBytes(byteData.ip, Endian.BIG);
        byteArrayBuilder.appendBytes(byteData.port, Endian.BIG);

        return byteArrayBuilder.build();
    }

    public NetworkAddress duplicate() {
        final NetworkAddress networkAddress = new NetworkAddress();

        networkAddress._timestamp = _timestamp;
        networkAddress._nodeFeatures.setFeaturesFlags(_nodeFeatures);
        networkAddress._ip = _ip.duplicate();
        networkAddress._port = _port;

        return networkAddress;
    }
}
