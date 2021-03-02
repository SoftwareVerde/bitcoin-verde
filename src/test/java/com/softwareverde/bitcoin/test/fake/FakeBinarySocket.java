package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.network.socket.BinarySocket;

public class FakeBinarySocket extends BinarySocket {
    public final FakeSocket fakeSocket;

    public FakeBinarySocket(final FakeSocket fakeSocket, final ThreadPool threadPool) {
        super(fakeSocket, BitcoinProtocolMessage.BINARY_PACKET_FORMAT, threadPool);
        this.fakeSocket = fakeSocket;
    }

    @Override
    public String getHost() {
        return "";
    }

    @Override
    public Integer getPort() {
        return 0;
    }
}