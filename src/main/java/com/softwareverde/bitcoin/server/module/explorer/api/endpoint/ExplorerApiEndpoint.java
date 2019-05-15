package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.server.module.stratum.rpc.StratumJsonRpcConnection;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.http.server.servlet.Servlet;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.io.Logger;

import java.net.Socket;

public abstract class ExplorerApiEndpoint implements Servlet {
    protected final Configuration.ExplorerProperties _explorerProperties;
    protected final ThreadPool _threadPool;

    public ExplorerApiEndpoint(final Configuration.ExplorerProperties explorerProperties, final ThreadPool threadPool) {
        _explorerProperties = explorerProperties;
        _threadPool = threadPool;
    }

    protected abstract Response _onRequest(final Request request);

    protected NodeJsonRpcConnection _getNodeJsonRpcConnection() {
        final String bitcoinRpcUrl = _explorerProperties.getBitcoinRpcUrl();
        final Integer bitcoinRpcPort = _explorerProperties.getBitcoinRpcPort();

        try {
            final Socket socket = new Socket(bitcoinRpcUrl, bitcoinRpcPort);
            if (socket.isConnected()) {
                return new NodeJsonRpcConnection(socket, _threadPool);
            }
        }
        catch (final Exception exception) {
            Logger.log(exception);
        }

        return null;
    }

    protected StratumJsonRpcConnection _getStratumJsonRpcConnection() {
        final String stratumRpcUrl = _explorerProperties.getStratumRpcUrl();
        final Integer stratumRpcPort = _explorerProperties.getStratumRpcPort();

        try {
            final Socket socket = new Socket(stratumRpcUrl, stratumRpcPort);
            if (socket.isConnected()) {
                return new StratumJsonRpcConnection(socket, _threadPool);
            }
        }
        catch (final Exception exception) {
            Logger.log(exception);
        }

        return null;
    }

    @Override
    public final Response onRequest(final Request request) {
        try {
            return this._onRequest(request);
        }
        catch (final Exception exception) {
            Logger.log(exception);
        }

        return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, "An internal error occurred."));
    }
}
