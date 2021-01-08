package com.softwareverde.bitcoin.rpc;

public class BitcoinNodeRpcAddress {
    protected final String _host;
    protected final Integer _port;
    protected final Boolean _isSecure;

    public BitcoinNodeRpcAddress(final String host, final Integer port) {
        this(host, port, false);
    }

    public BitcoinNodeRpcAddress(final String host, final Integer port, final Boolean isSecure) {
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
