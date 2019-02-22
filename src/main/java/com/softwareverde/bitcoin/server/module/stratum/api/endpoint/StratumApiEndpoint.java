package com.softwareverde.bitcoin.server.module.stratum.api.endpoint;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.explorer.api.ApiResult;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeJsonRpcConnection;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.io.Logger;
import com.softwareverde.servlet.Servlet;
import com.softwareverde.servlet.request.Request;
import com.softwareverde.servlet.response.JsonResponse;
import com.softwareverde.servlet.response.Response;

import java.net.Socket;

public abstract class StratumApiEndpoint implements Servlet {
    protected final Configuration.StratumProperties _stratumProperties;
    protected final StratumDataHandler _stratumDataHandler;
    protected final ThreadPool _threadPool;

    public StratumApiEndpoint(final Configuration.StratumProperties stratumProperties, final StratumDataHandler stratumDataHandler, final ThreadPool threadPool) {
        _stratumProperties = stratumProperties;
        _stratumDataHandler = stratumDataHandler;
        _threadPool = threadPool;
    }

    protected abstract Response _onRequest(final Request request);

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
