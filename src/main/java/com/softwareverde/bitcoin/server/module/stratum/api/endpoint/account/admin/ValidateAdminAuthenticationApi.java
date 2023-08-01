package com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account.admin;

import com.softwareverde.bitcoin.server.configuration.StratumProperties;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiEndpoint;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiResult;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.querystring.PostParameters;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.servlet.session.Session;

public class ValidateAdminAuthenticationApi extends StratumApiEndpoint {
    public ValidateAdminAuthenticationApi(final StratumProperties stratumProperties) {
        super(stratumProperties);
    }

    @Override
    protected Response _onRequest(final Request request) {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        final HttpMethod httpMethod = request.getMethod();
        if (httpMethod != HttpMethod.GET) {
            return new JsonResponse(Response.Codes.BAD_REQUEST, new StratumApiResult(false, "Invalid method."));
        }

        final Session session = _sessionManager.getSession(request);
        if (session == null) {
            return new JsonResponse(Response.Codes.NOT_AUTHORIZED, new StratumApiResult(false, "Not authorized."));
        }

        final Json sessionJson = session.getMutableData();
        if (! sessionJson.getBoolean("isAdmin")) {
            return new JsonResponse(Response.Codes.NOT_AUTHORIZED, new StratumApiResult(false, "Not authorized."));
        }

        return new JsonResponse(Response.Codes.OK, new StratumApiResult(true, null));
    }
}
