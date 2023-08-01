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
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.servlet.session.Session;

public class CreateAccountApi extends StratumApiEndpoint {
    public static final Integer MIN_PASSWORD_LENGTH = 8;

    protected final DatabaseConnectionFactory _databaseConnectionFactory;

    public CreateAccountApi(final StratumProperties stratumProperties, final DatabaseConnectionFactory databaseConnectionFactory) {
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

        {   // CREATE ACCOUNT
            // Requires GET:
            // Requires POST: email, password

            final String email = postParameters.get("email").trim();
            if (email.isEmpty()) {
                return new JsonResponse(Response.Codes.BAD_REQUEST, new StratumApiResult(false, "Invalid email address."));
            }

            final String password = postParameters.get("password");
            if (password.length() < MIN_PASSWORD_LENGTH) {
                return new JsonResponse(Response.Codes.BAD_REQUEST, new StratumApiResult(false, "Invalid password length."));
            }

            try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                final AccountDatabaseManager accountDatabaseManager = new AccountDatabaseManager(databaseConnection);

                { // Check for existing account...
                    final AccountId accountId = accountDatabaseManager.getAccountId(email);
                    if (accountId != null) {
                        return new JsonResponse(Response.Codes.OK, new StratumApiResult(false, "An account with that email address already exists."));
                    }
                }

                final AccountId accountId = accountDatabaseManager.createAccount(email, password);
                if (accountId == null) {
                    return new JsonResponse(Response.Codes.SERVER_ERROR, new StratumApiResult(false, "Unable to create account."));
                }

                final Response response = new JsonResponse(Response.Codes.OK, new StratumApiResult(true, null));

                final Session session = _sessionManager.createSession(request, response);
                final Json sessionData = session.getMutableData();
                sessionData.put("accountId", accountId);
                _sessionManager.saveSession(session);

                return response;
            }
            catch (final DatabaseException exception) {
                Logger.warn(exception);
                return new JsonResponse(Response.Codes.SERVER_ERROR, new StratumApiResult(false, "An internal error occurred."));
            }
        }
    }
}
