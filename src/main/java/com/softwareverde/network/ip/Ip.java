package com.softwareverde.network.ip;

public interface Ip {
    byte[] getBytes();
    Ip copy();

    @Override
    String toString();
}
