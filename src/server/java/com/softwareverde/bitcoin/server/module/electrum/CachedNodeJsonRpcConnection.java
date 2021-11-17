package com.softwareverde.bitcoin.server.module.electrum;

import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.concurrent.threadpool.ThreadPool;

import java.net.Socket;

public class CachedNodeJsonRpcConnection extends NodeJsonRpcConnection {
    protected Runnable _onClose;

    public CachedNodeJsonRpcConnection(final String hostname, final Integer port, final ThreadPool threadPool) {
        super(hostname, port, threadPool);
    }

    public CachedNodeJsonRpcConnection(final String hostname, final Integer port, final ThreadPool threadPool, final MasterInflater masterInflater) {
        super(hostname, port, threadPool, masterInflater);
    }

    public CachedNodeJsonRpcConnection(final Socket javaSocket, final ThreadPool threadPool) {
        super(javaSocket, threadPool);
    }

    public CachedNodeJsonRpcConnection(final Socket socket, final ThreadPool threadPool, final MasterInflater masterInflater) {
        super(socket, threadPool, masterInflater);
    }

    public void setOnCloseCallback(final Runnable onClose) {
        _onClose = onClose;
    }

    @Override
    public void close() {
        final Runnable callback = _onClose;
        if (callback != null) {
            callback.run();
        }
        else {
            super.close();
        }
    }

    public void superClose() {
        super.close();
    }
}
