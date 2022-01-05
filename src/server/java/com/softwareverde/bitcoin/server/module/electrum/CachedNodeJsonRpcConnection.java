package com.softwareverde.bitcoin.server.module.electrum;

import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.util.type.time.SystemTime;

import java.net.Socket;

public class CachedNodeJsonRpcConnection extends NodeJsonRpcConnection {
    protected final SystemTime _systemTime = new SystemTime();
    protected final Long _creationTime;
    protected Runnable _onClose;

    public CachedNodeJsonRpcConnection(final String hostname, final Integer port, final ThreadPool threadPool) {
        super(hostname, port, threadPool);

        _creationTime = _systemTime.getCurrentTimeInMilliSeconds();
    }

    public CachedNodeJsonRpcConnection(final String hostname, final Integer port, final ThreadPool threadPool, final MasterInflater masterInflater) {
        super(hostname, port, threadPool, masterInflater);

        _creationTime = _systemTime.getCurrentTimeInMilliSeconds();
    }

    public CachedNodeJsonRpcConnection(final Socket javaSocket, final ThreadPool threadPool) {
        super(javaSocket, threadPool);

        _creationTime = _systemTime.getCurrentTimeInMilliSeconds();
    }

    public CachedNodeJsonRpcConnection(final Socket socket, final ThreadPool threadPool, final MasterInflater masterInflater) {
        super(socket, threadPool, masterInflater);

        _creationTime = _systemTime.getCurrentTimeInMilliSeconds();
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

    public Long getCreationTime() {
        return _creationTime;
    }

    public Long getConnectionDuration() {
        final Long now = _systemTime.getCurrentTimeInMilliSeconds();
        return (now - _creationTime);
    }
}
