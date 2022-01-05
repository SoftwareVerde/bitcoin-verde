package com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account.worker;

import com.softwareverde.bitcoin.miner.pool.AccountId;
import com.softwareverde.bitcoin.miner.pool.WorkerId;
import com.softwareverde.bitcoin.server.configuration.StratumProperties;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiResult;
import com.softwareverde.bitcoin.server.module.stratum.database.WorkerDatabaseManager;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.querystring.PostParameters;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.servlet.AuthenticatedServlet;
import com.softwareverde.util.Util;

public class GetWorkersApi extends AuthenticatedServlet {
    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected Long _shareDifficulty;

    public GetWorkersApi(final StratumProperties stratumProperties, final DatabaseConnectionFactory databaseConnectionFactory) {
        super(stratumProperties);
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    public void setShareDifficulty(final Long shareDifficulty) {
        _shareDifficulty = shareDifficulty;
    }

    @Override
    protected Response _onAuthenticatedRequest(final AccountId accountId, final Request request) {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        if (request.getMethod() != HttpMethod.GET) {
            return new JsonResponse(Response.Codes.BAD_REQUEST, new StratumApiResult(false, "Invalid method."));
        }

        {   // GET WORKERS
            // Requires GET:
            // Requires POST:

            try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                final WorkerDatabaseManager workerDatabaseManager = new WorkerDatabaseManager(databaseConnection);
                final List<WorkerId> workerIds = workerDatabaseManager.getWorkerIds(accountId);

                final Json workersJson = new Json(true);

                for (final WorkerId workerId : workerIds) {
                    final String workerUsername = workerDatabaseManager.getWorkerUsername(workerId);

                    final Long shareDifficulty = _shareDifficulty;
                    final Long totalWorkerShareCount = workerDatabaseManager.getTotalWorkerShares(workerId);
                    final Long normalizedWorkerShareCount = (totalWorkerShareCount / Util.coalesce(shareDifficulty, 1L));

                    final Json workerJson = new Json(false);
                    workerJson.put("id", workerId);
                    workerJson.put("username", workerUsername);
                    workerJson.put("sharesCount", normalizedWorkerShareCount);
                    workersJson.add(workerJson);
                }

                final StratumApiResult result = new StratumApiResult(true, null);
                result.put("workers", workersJson);
                return new JsonResponse(Response.Codes.OK, result);
            }
            catch (final DatabaseException exception) {
                Logger.warn(exception);
                return new JsonResponse(Response.Codes.SERVER_ERROR, new StratumApiResult(false, "An internal error occurred."));
            }
        }
    }
}
