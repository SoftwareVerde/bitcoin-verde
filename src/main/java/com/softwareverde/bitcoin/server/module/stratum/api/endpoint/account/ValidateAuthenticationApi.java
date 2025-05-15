package com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account;

import com.softwareverde.bitcoin.miner.pool.AccountId;
import com.softwareverde.bitcoin.server.configuration.StratumProperties;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiResult;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.querystring.PostParameters;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.servlet.AuthenticatedServlet;

public class ValidateAuthenticationApi extends AuthenticatedServlet {

    public ValidateAuthenticationApi(final StratumProperties stratumProperties) {
        super(stratumProperties);
    }

    @Override
    protected Response _onAuthenticatedRequest(final AccountId accountId, final Request request) {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        {   // VALIDATE AUTHENTICATION TOKEN
            // Requires GET:
            // Requires POST:

            return new JsonResponse(Response.Codes.OK, new StratumApiResult(true, null));
        }
    }
}
