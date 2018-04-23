package com.softwareverde.bitcoin.server.network;

public class NetworkTime {
    public Long getCurrentTime() {
        return System.currentTimeMillis() / 1000L; // TODO
    }
}
