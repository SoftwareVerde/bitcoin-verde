package com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account;

import com.softwareverde.bitcoin.miner.pool.AccountId;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiEndpoint;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiResult;
import com.softwareverde.bitcoin.server.module.stratum.database.AccountDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.servlet.GetParameters;
import com.softwareverde.servlet.PostParameters;
import com.softwareverde.servlet.request.Request;
import com.softwareverde.servlet.response.JsonResponse;
import com.softwareverde.servlet.response.Response;
import com.softwareverde.servlet.session.Session;

import static com.softwareverde.servlet.response.Response.ResponseCodes;

public class CreateAccountApi extends StratumApiEndpoint {
    public static final Integer MIN_PASSWORD_LENGTH = 8;

    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;

    public CreateAccountApi(final Configuration.StratumProperties stratumProperties, final MysqlDatabaseConnectionFactory databaseConnectionFactory) {
        super(stratumProperties);

        _databaseConnectionFactory = databaseConnectionFactory;
    }

    @Override
    protected Response _onRequest(final Request request) {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        if (request.getMethod() != Request.HttpMethod.POST) {
            return new JsonResponse(ResponseCodes.BAD_REQUEST, new StratumApiResult(false, "Invalid method."));
        }

        {   // CREATE ACCOUNT
            // Requires GET:
            // Requires POST: email, password

            final String email = postParameters.get("email").trim();
            if (email.isEmpty()) {
                return new JsonResponse(ResponseCodes.BAD_REQUEST, new StratumApiResult(false, "Invalid email address."));
            }

            final String password = postParameters.get("password");
            if (password.length() < MIN_PASSWORD_LENGTH) {
                return new JsonResponse(ResponseCodes.BAD_REQUEST, new StratumApiResult(false, "Invalid password length."));
            }

            try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                final AccountDatabaseManager accountDatabaseManager = new AccountDatabaseManager(databaseConnection);

                { // Check for existing account...
                    final AccountId accountId = accountDatabaseManager.getAccountId(email);
                    if (accountId != null) {
                        return new JsonResponse(ResponseCodes.OK, new StratumApiResult(false, "An account with that email address already exists."));
                    }
                }

                final AccountId accountId = accountDatabaseManager.createAccount(email, password);
                if (accountId == null) {
                    return new JsonResponse(ResponseCodes.SERVER_ERROR, new StratumApiResult(false, "Unable to create account."));
                }

                final Response response = new JsonResponse(ResponseCodes.OK, new StratumApiResult(true, null));

                final Session session = _sessionManager.createSession(request, response);
                final Json sessionData = session.getMutableData();
                sessionData.put("accountId", accountId);
                _sessionManager.saveSession(session);

                return response;
            }
            catch (final DatabaseException exception) {
                Logger.log(exception);
                return new JsonResponse(ResponseCodes.SERVER_ERROR, new StratumApiResult(false, "An internal error occurred."));
            }
        }
    }
}
