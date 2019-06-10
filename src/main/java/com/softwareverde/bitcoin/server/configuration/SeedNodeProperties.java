package com.softwareverde.bitcoin.server.configuration;

public class SeedNodeProperties {
    protected final String _address;
    protected final Integer _port;

    public SeedNodeProperties(final String address, final Integer port) {
        _address = address;
        _port = port;
    }

    public String getAddress() { return _address; }
    public Integer getPort() { return _port; }
}