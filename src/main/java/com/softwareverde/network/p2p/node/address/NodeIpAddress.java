package com.softwareverde.network.p2p.node.address;

import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.ip.IpInflater;
import com.softwareverde.network.ip.Ipv4;

public class NodeIpAddress {
    protected Ip _ip;
    protected Integer _port;

    public NodeIpAddress() {
        _ip = new Ipv4();
        _port = 0x0000;
    }

    public NodeIpAddress(final Ip ip, final Integer port) {
        _ip = ip;
        _port = port;
    }

    public void setIp(final Ip ip) {
        _ip = ( (ip != null) ? ip.copy() : new Ipv4());
    }

    public Ip getIp() {
        return _ip.copy();
    }

    public void setPort(final Integer port) { _port = port; }
    public Integer getPort() { return _port; }

    public NodeIpAddress copy() {
        final NodeIpAddress nodeIpAddress = new NodeIpAddress();

        nodeIpAddress._ip = _ip.copy();
        nodeIpAddress._port = _port;

        return nodeIpAddress;
    }

    @Override
    public boolean equals(final Object object) {
        if (object == null) { return false; }
        if (! (object instanceof NodeIpAddress)) { return false; }
        final NodeIpAddress nodeIpAddress = (NodeIpAddress) object;
        if (! _ip.equals(nodeIpAddress._ip)) { return false; }
        return _port.equals(nodeIpAddress._port);
    }

    @Override
    public int hashCode() {
        return (_ip.hashCode() + _port.hashCode());
    }

    @Override
    public String toString() {
        return (_ip.toString() + ":" + _port);
    }
}
