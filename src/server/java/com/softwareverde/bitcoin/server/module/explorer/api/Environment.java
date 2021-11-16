package com.softwareverde.bitcoin.server.module.explorer.api;

import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.server.configuration.ExplorerProperties;
import com.softwareverde.bitcoin.server.module.stratum.rpc.StratumJsonRpcConnection;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.logging.Logger;

import java.net.Socket;

public class Environment implements com.softwareverde.http.server.servlet.routed.Environment {
    protected final ExplorerProperties _explorerProperties;
    protected final ThreadPool _threadPool;

    public Environment(final ExplorerProperties explorerProperties, final ThreadPool threadPool) {
        _explorerProperties = explorerProperties;
        _threadPool = threadPool;
    }

    public ExplorerProperties getExplorerProperties() {
        return _explorerProperties;
    }

    public ThreadPool getThreadPool() {
        return _threadPool;
    }

    public NodeJsonRpcConnection getNodeJsonRpcConnection() {
        final String bitcoinRpcUrl = _explorerProperties.getBitcoinRpcUrl();
        final Integer bitcoinRpcPort = _explorerProperties.getBitcoinRpcPort();

        try {
            final Socket socket = new Socket(bitcoinRpcUrl, bitcoinRpcPort);
            if (socket.isConnected()) {
                return new NodeJsonRpcConnection(socket, _threadPool);
            }
        }
        catch (final Exception exception) {
            Logger.warn(exception);
        }

        return null;
    }

    public StratumJsonRpcConnection getStratumJsonRpcConnection() {
        final String stratumRpcUrl = _explorerProperties.getStratumRpcUrl();
        final Integer stratumRpcPort = _explorerProperties.getStratumRpcPort();

        try {
            final Socket socket = new Socket(stratumRpcUrl, stratumRpcPort);
            if (socket.isConnected()) {
                return new StratumJsonRpcConnection(socket, _threadPool);
            }
        }
        catch (final Exception exception) {
            Logger.warn(exception);
        }

        return null;
    }
}
