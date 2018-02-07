package com.softwareverde.bitcoin.server.socket.message.networkaddress.ip;

public interface Ip {
    byte[] getBytes();
    Ip duplicate();
}
