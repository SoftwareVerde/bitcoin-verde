package com.softwareverde.bitcoin.server.module.node.rpc.core;

public class BitcoinNodeAddress {
    protected final String _host;
    protected final Integer _port;
    protected final Boolean _isSecure;

    public BitcoinNodeAddress(final String host, final Integer port) {
        this(host, port, false);
    }

    public BitcoinNodeAddress(final String host, final Integer port, final Boolean isSecure) {
        _host = host;
        _port = port;
        _isSecure = isSecure;
    }

    public String getHost() {
        return _host;
    }

    public Integer getPort() {
        return _port;
    }

    public Boolean isSecure() {
        return _isSecure;
    }

    @Override
    public String toString() {
        return (_host + ":" + _port);
    }
}
