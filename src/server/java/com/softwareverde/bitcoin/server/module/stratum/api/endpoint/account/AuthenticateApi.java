package com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account;

import com.softwareverde.bitcoin.miner.pool.AccountId;
import com.softwareverde.bitcoin.server.configuration.StratumProperties;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiEndpoint;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiResult;
import com.softwareverde.bitcoin.server.module.stratum.database.AccountDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.querystring.PostParameters;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.servlet.session.Session;

public class AuthenticateApi extends StratumApiEndpoint {
    protected final DatabaseConnectionFactory _databaseConnectionFactory;

    public AuthenticateApi(final StratumProperties stratumProperties, final DatabaseConnectionFactory databaseConnectionFactory) {
        super(stratumProperties);

        _databaseConnectionFactory = databaseConnectionFactory;
    }

    @Override
    protected Response _onRequest(final Request request) {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        if (request.getMethod() != HttpMethod.POST) {
            return new JsonResponse(Response.Codes.BAD_REQUEST, new StratumApiResult(false, "Invalid method."));
        }

        {   // AUTHENTICATE
            // Requires GET:
            // Requires POST: email, password

            final String email = postParameters.get("email");
            if (email.isEmpty()) {
                return new JsonResponse(Response.Codes.BAD_REQUEST, new StratumApiResult(false, "Invalid email address."));
            }

            final String password = postParameters.get("password");

            try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                final AccountDatabaseManager accountDatabaseManager = new AccountDatabaseManager(databaseConnection);

                final AccountId accountId = accountDatabaseManager.authenticateAccount(email, password);
                if (accountId == null) {
                    return new JsonResponse(Response.Codes.OK, new StratumApiResult(false, "Invalid credentials."));
                }

                final Response response = new JsonResponse(Response.Codes.OK, new StratumApiResult(true, null));

                final Session session = _sessionManager.createSession(request, response);
                final Json sessionData = session.getMutableData();
                sessionData.put("accountId", accountId);
                _sessionManager.saveSession(session);

                return response;
            }
            catch (final DatabaseException exception) {
                Logger.log(exception);
                return new JsonResponse(Response.Codes.SERVER_ERROR, new StratumApiResult(false, "An internal error occurred."));
            }
        }
    }
}
