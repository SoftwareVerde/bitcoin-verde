package com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account.worker;

import com.softwareverde.bitcoin.miner.pool.AccountId;
import com.softwareverde.bitcoin.miner.pool.WorkerId;
import com.softwareverde.bitcoin.server.configuration.StratumProperties;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiResult;
import com.softwareverde.bitcoin.server.module.stratum.database.WorkerDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.querystring.PostParameters;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.logging.Logger;
import com.softwareverde.servlet.AuthenticatedServlet;

public class CreateWorkerApi extends AuthenticatedServlet {
    protected final DatabaseConnectionFactory _databaseConnectionFactory;

    public CreateWorkerApi(final StratumProperties stratumProperties, final DatabaseConnectionFactory databaseConnectionFactory) {
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

            try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                final WorkerDatabaseManager workerDatabaseManager = new WorkerDatabaseManager(databaseConnection);
                final WorkerId existingWorkerId = workerDatabaseManager.getWorkerId(username);
                if (existingWorkerId != null) {
                    return new JsonResponse(Response.Codes.OK, new StratumApiResult(false, "A worker with that username already exists."));
                }

                final WorkerId workerId = workerDatabaseManager.createWorker(accountId, username, password);
                if (workerId == null) {
                    return new JsonResponse(Response.Codes.SERVER_ERROR, new StratumApiResult(false, "Unable to create worker."));
                }

                final StratumApiResult result = new StratumApiResult(true, null);
                result.put("workerId", workerId);
                return new JsonResponse(Response.Codes.OK, result);
            }
            catch (final DatabaseException exception) {
                Logger.warn(exception);
                return new JsonResponse(Response.Codes.SERVER_ERROR, new StratumApiResult(false, "An internal error occurred."));
            }
        }
    }
}
