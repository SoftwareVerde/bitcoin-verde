package com.softwareverde.bitcoin.server.module.stratum.api.endpoint;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.http.server.servlet.Servlet;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.io.Logger;
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

        return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, "An internal error occurred."));
    }
}
