package com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account;

import com.softwareverde.bitcoin.miner.pool.AccountId;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiResult;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.querystring.PostParameters;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.servlet.AuthenticatedServlet;

public class UnauthenticateApi extends AuthenticatedServlet {
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;

    public UnauthenticateApi(final Configuration.StratumProperties stratumProperties, final MysqlDatabaseConnectionFactory databaseConnectionFactory) {
        super(stratumProperties);

        _databaseConnectionFactory = databaseConnectionFactory;
    }

    @Override
    protected Response _onAuthenticatedRequest(final AccountId accountId, final Request request) {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        if (request.getMethod() != HttpMethod.POST) {
            return new JsonResponse(Response.Codes.BAD_REQUEST, new StratumApiResult(false, "Invalid method."));
        }

        {   // AUTHENTICATE
            // Requires GET:
            // Requires POST:

            final Response response = new JsonResponse(Response.Codes.OK, new StratumApiResult(true, null));
            _sessionManager.destroySession(request, response);
            return response;
        }
    }
}
