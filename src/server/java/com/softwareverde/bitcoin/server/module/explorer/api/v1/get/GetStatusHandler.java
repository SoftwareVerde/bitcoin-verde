package com.softwareverde.bitcoin.server.module.explorer.api.v1.get;

import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.StatusApi;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.http.server.servlet.routed.RequestHandler;
import com.softwareverde.json.Json;

import java.util.Map;

public class GetStatusHandler implements RequestHandler<Environment> {

    /**
     * CHECK STATUS
     * Requires GET:
     * Requires POST:
     */
    @Override
    public Response handleRequest(final Request request, final Environment environment, final Map<String, String> parameters) throws Exception {
        try (final NodeJsonRpcConnection nodeJsonRpcConnection = environment.getNodeJsonRpcConnection()) {
            if (nodeJsonRpcConnection == null) {
                final StatusApi.StatusResult result = new StatusApi.StatusResult();
                result.setWasSuccess(false);
                result.setErrorMessage("Unable to connect to node.");
                return new JsonResponse(Response.Codes.SERVER_ERROR, result);
            }

            final String status;
            final Json statisticsJson;
            final Json utxoCacheStatusJson;
            final Json serverLoadJson;
            final Json serviceStatusesJson;
            {
                final Json rpcResponseJson = nodeJsonRpcConnection.getStatus();
                if (rpcResponseJson == null) {
                    return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                }

                if (! rpcResponseJson.getBoolean("wasSuccess")) {
                    final String errorMessage = rpcResponseJson.getString("errorMessage");
                    return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, errorMessage));
                }

                statisticsJson = rpcResponseJson.get("statistics");
                utxoCacheStatusJson = rpcResponseJson.get("utxoCacheStatus");
                status = rpcResponseJson.getString("status");
                serverLoadJson = rpcResponseJson.get("serverLoad");
                serviceStatusesJson = rpcResponseJson.get("serviceStatuses");
            }

            final StatusApi.StatusResult statusResult = new StatusApi.StatusResult();
            statusResult.setWasSuccess(true);
            statusResult.setStatus(status);
            statusResult.setStatistics(statisticsJson);
            statusResult.setUtxoCacheStatus(utxoCacheStatusJson);
            statusResult.setServerLoad(serverLoadJson);
            statusResult.setServiceStatuses(serviceStatusesJson);
            return new JsonResponse(Response.Codes.OK, statusResult);
        }
    }
}
