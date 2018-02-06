package com.softwareverde.bitcoin.server.socket.message.networkaddress;

import com.softwareverde.bitcoin.server.socket.message.BitcoinServiceType;

public class NetworkAddress {
    static class ByteData {
        public final byte[] timestamp = new byte[4];
        public final byte[] serviceType = new byte[8];
        public final byte[] ip = new byte[16];
        public final byte[] port = new byte[2];
    }

    private Long _timestamp;
    private BitcoinServiceType _serviceType;

    public void copyFrom(final NetworkAddress networkAddress) {

    }

    public byte[] getBytes() {
        return new byte[0];
    }
}
