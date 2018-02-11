package com.softwareverde.bitcoin.server.message.type.node.address;

import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.socket.ip.Ip;
import com.softwareverde.bitcoin.server.socket.ip.Ipv4;
import com.softwareverde.bitcoin.server.socket.ip.Ipv6;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class NodeIpAddress {
    public static final Integer BYTE_COUNT_WITHOUT_TIMESTAMP = 26;
    public static final Integer BYTE_COUNT_WITH_TIMESTAMP = 30;

    private static class ByteData {
        public final byte[] timestamp = new byte[4];
        public final byte[] nodeFeatureFlags = new byte[8];
        public final byte[] ip = new byte[16];
        public final byte[] port = new byte[2];
    }

    protected Long _timestamp;
    protected final NodeFeatures _nodeFeatures;
    protected Ip _ip;
    protected Integer _port;

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

    public NodeIpAddress() {
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

    public NodeIpAddress duplicate() {
        final NodeIpAddress nodeIpAddress = new NodeIpAddress();

        nodeIpAddress._timestamp = _timestamp;
        nodeIpAddress._nodeFeatures.setFeaturesFlags(_nodeFeatures);
        nodeIpAddress._ip = _ip.duplicate();
        nodeIpAddress._port = _port;

        return nodeIpAddress;
    }
}
