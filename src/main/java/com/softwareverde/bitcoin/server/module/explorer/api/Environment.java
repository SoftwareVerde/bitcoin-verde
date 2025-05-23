package com.softwareverde.bitcoin.server.module.explorer.api;

import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.server.configuration.ExplorerProperties;
import com.softwareverde.bitcoin.server.module.stratum.rpc.StratumJsonRpcConnection;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.logging.Logger;

import java.net.InetSocketAddress;
import java.net.Socket;

public class Environment implements com.softwareverde.http.server.servlet.routed.Environment {
    protected final ExplorerProperties _explorerProperties;

    public Environment(final ExplorerProperties explorerProperties) {
        _explorerProperties = explorerProperties;
    }

    public ExplorerProperties getExplorerProperties() {
        return _explorerProperties;
    }

    public NodeJsonRpcConnection getNodeJsonRpcConnection() {
        final String bitcoinRpcUrl = _explorerProperties.getBitcoinRpcUrl();
        final Integer bitcoinRpcPort = _explorerProperties.getBitcoinRpcPort();

        try {
            final Socket socket = new Socket();
            socket.connect(new InetSocketAddress(bitcoinRpcUrl, bitcoinRpcPort), 3000);
            if (socket.isConnected()) {
                return new NodeJsonRpcConnection(socket);
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
            final Socket socket = new Socket();
            socket.connect(new InetSocketAddress(stratumRpcUrl, stratumRpcPort), 3000);
            if (socket.isConnected()) {
                return new StratumJsonRpcConnection(socket);
            }
        }
        catch (final Exception exception) {
            Logger.warn(exception);
        }

        return null;
    }
}
