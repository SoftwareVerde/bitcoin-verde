package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.explorer.api.ApiResult;
import com.softwareverde.io.Logger;
import com.softwareverde.servlet.Servlet;
import com.softwareverde.servlet.request.Request;
import com.softwareverde.servlet.response.JsonResponse;
import com.softwareverde.servlet.response.Response;
import com.softwareverde.socket.SocketConnection;

import java.net.Socket;

public abstract class ExplorerApiEndpoint implements Servlet {
    protected final Configuration.ExplorerProperties _explorerProperties;

    public ExplorerApiEndpoint(final Configuration.ExplorerProperties explorerProperties) {
        _explorerProperties = explorerProperties;
    }

    protected abstract Response _onRequest(final Request request);

    protected SocketConnection _newRpcConnection() {
        final String bitcoinRpcUrl = _explorerProperties.getBitcoinRpcUrl();
        final Integer bitcoinRpcPort = _explorerProperties.getBitcoinRpcPort();

        try {
            final Socket socket = new Socket(bitcoinRpcUrl, bitcoinRpcPort);
            if (socket.isConnected()) {
                return new SocketConnection(socket);
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

        return new JsonResponse(Response.ResponseCodes.SERVER_ERROR, new ApiResult(false, "An internal error occurred."));
    }
}
