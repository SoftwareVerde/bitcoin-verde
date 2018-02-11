package com.softwareverde.bitcoin.server.socket.ip;

public interface Ip {
    byte[] getBytes();
    Ip duplicate();
}
