package com.softwareverde.servlet;

import com.softwareverde.bitcoin.miner.pool.AccountId;
import com.softwareverde.bitcoin.server.configuration.StratumProperties;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiEndpoint;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiResult;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.servlet.session.Session;

public abstract class AuthenticatedServlet extends StratumApiEndpoint {
    public AuthenticatedServlet(final StratumProperties stratumProperties) {
        super(stratumProperties);
    }

    @Override
    protected final Response _onRequest(final Request request) {
        final Session session = _sessionManager.getSession(request);
        if (session == null) {
            return new JsonResponse(Response.Codes.OK, new StratumApiResult(false, "Not authenticated."));
        }

        final Json sessionData = session.getMutableData();
        final AccountId accountId = AccountId.wrap(sessionData.getLong("accountId"));
        if (accountId == null) {
            return new JsonResponse(Response.Codes.OK, new StratumApiResult(false, "Not authenticated."));
        }

        return _onAuthenticatedRequest(accountId, request);
    }

    protected abstract Response _onAuthenticatedRequest(final AccountId accountId, final Request request);
}
