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
import com.softwareverde.util.Util;

public class DeleteWorkerApi extends AuthenticatedServlet {
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;

    public DeleteWorkerApi(final Configuration.StratumProperties stratumProperties, final MysqlDatabaseConnectionFactory databaseConnectionFactory) {
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

        {   // DELETE WORKER
            // Requires GET:
            // Requires POST: workerId

            final WorkerId workerId = WorkerId.wrap(Util.parseLong(postParameters.get("workerId")));
            if ( (workerId == null) || (workerId.longValue() < 1) ) {
                return new JsonResponse(Response.Codes.BAD_REQUEST, new StratumApiResult(false, "Invalid worker id."));
            }

            try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                final AccountDatabaseManager accountDatabaseManager = new AccountDatabaseManager(databaseConnection);
                final AccountId workerAccountId = accountDatabaseManager.getWorkerAccountId(workerId);
                if (! Util.areEqual(accountId, workerAccountId)) {
                    return new JsonResponse(Response.Codes.BAD_REQUEST, new StratumApiResult(false, "Invalid worker id."));
                }

                accountDatabaseManager.deleteWorker(workerId);

                return new JsonResponse(Response.Codes.OK, new StratumApiResult(true, null));
            }
            catch (final DatabaseException exception) {
                Logger.log(exception);
                return new JsonResponse(Response.Codes.SERVER_ERROR, new StratumApiResult(false, "An internal error occurred."));
            }
        }
    }
}
