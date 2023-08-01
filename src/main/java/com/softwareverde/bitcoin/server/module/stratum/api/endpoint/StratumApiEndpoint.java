package com.softwareverde.bitcoin.server.module.stratum.api.endpoint;

import com.softwareverde.bitcoin.server.configuration.StratumProperties;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.http.server.servlet.Servlet;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.logging.Logger;
import com.softwareverde.servlet.session.SessionManager;

public abstract class StratumApiEndpoint implements Servlet {
    protected final StratumProperties _stratumProperties;
    protected final SessionManager _sessionManager;

    public StratumApiEndpoint(final StratumProperties stratumProperties) {
        _stratumProperties = stratumProperties;
        final Boolean enableSecureCookies = stratumProperties.areSecureCookiesEnabled();
        final String cookiesDirectory = (_stratumProperties.getCookiesDirectory() + "/");
        _sessionManager = new SessionManager(cookiesDirectory, enableSecureCookies);
    }

    protected abstract Response _onRequest(final Request request);

    @Override
    public final Response onRequest(final Request request) {
        try {
            return this._onRequest(request);
        }
        catch (final Exception exception) {
            Logger.warn(exception);
        }

        return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, "An internal error occurred."));
    }
}
