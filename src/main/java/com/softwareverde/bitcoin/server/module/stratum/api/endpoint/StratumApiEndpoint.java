package com.softwareverde.bitcoin.server.module.stratum.api.endpoint;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.io.Logger;
import com.softwareverde.servlet.Servlet;
import com.softwareverde.servlet.request.Request;
import com.softwareverde.servlet.response.JsonResponse;
import com.softwareverde.servlet.response.Response;
import com.softwareverde.servlet.session.SessionManager;

public abstract class StratumApiEndpoint implements Servlet {
    protected final Configuration.StratumProperties _stratumProperties;
    protected final SessionManager _sessionManager;

    public StratumApiEndpoint(final Configuration.StratumProperties stratumProperties) {
        _stratumProperties = stratumProperties;
        _sessionManager = new SessionManager(_stratumProperties.getCookiesDirectory() + "/");
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
