package com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account.worker;

import com.softwareverde.bitcoin.miner.pool.AccountId;
import com.softwareverde.bitcoin.miner.pool.WorkerId;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiResult;
import com.softwareverde.bitcoin.server.module.stratum.database.AccountDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.querystring.PostParameters;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.io.Logger;
import com.softwareverde.servlet.AuthenticatedServlet;

public class CreateWorkerApi extends AuthenticatedServlet {
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;

    public CreateWorkerApi(final Configuration.StratumProperties stratumProperties, final MysqlDatabaseConnectionFactory databaseConnectionFactory) {
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

        {   // CREATE WORKER
            // Requires GET:
            // Requires POST: username, password

            final String username = postParameters.get("username").trim();
            final String password = postParameters.get("password");

            if (username.isEmpty()) {
                return new JsonResponse(Response.Codes.BAD_REQUEST, new StratumApiResult(false, "Invalid worker username."));
            }

            try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                final AccountDatabaseManager accountDatabaseManager = new AccountDatabaseManager(databaseConnection);
                final WorkerId existingWorkerId = accountDatabaseManager.getWorkerId(username);
                if (existingWorkerId != null) {
                    return new JsonResponse(Response.Codes.OK, new StratumApiResult(false, "A worker with that username already exists."));
                }

                final WorkerId workerId = accountDatabaseManager.createWorker(accountId, username, password);
                if (workerId == null) {
                    return new JsonResponse(Response.Codes.SERVER_ERROR, new StratumApiResult(false, "Unable to create worker."));
                }

                final StratumApiResult result = new StratumApiResult(true, null);
                result.put("workerId", workerId);
                return new JsonResponse(Response.Codes.OK, result);
            }
            catch (final DatabaseException exception) {
                Logger.log(exception);
                return new JsonResponse(Response.Codes.SERVER_ERROR, new StratumApiResult(false, "An internal error occurred."));
            }
        }
    }
}
