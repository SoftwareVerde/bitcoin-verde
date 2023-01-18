package com.softwareverde.bitcoin.server.module.explorer.api.v1.get;

import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.address.ParsedAddress;
import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.AddressesApi;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.http.server.servlet.routed.RequestHandler;
import com.softwareverde.json.Json;
import com.softwareverde.util.Util;

import java.util.Map;


public class GetAddressBalanceHandler implements RequestHandler<Environment> {

    protected final AddressInflater _addressInflater = new AddressInflater();

    /**
     * GET ADDRESS TRANSACTION
     * Requires GET:    address
     * Requires POST:
     */
    @Override
    public Response handleRequest(final Request request, final Environment environment, final Map<String, String> urlParameters) throws Exception {
        final String addressString;
        final ParsedAddress address;
        {
            final GetParameters getParameters = request.getGetParameters();
            addressString = Util.coalesce(urlParameters.get("address"), getParameters.get("address"));
            if (! Util.isBlank(addressString)) {
                final ParsedAddress base58Address = _addressInflater.fromBase58Check(addressString);
                final ParsedAddress base32Address = _addressInflater.fromBase32Check(addressString);
                address = Util.coalesce(base58Address, base32Address);
            }
            else {
                address = null;
            }
        }

        if (address == null) {
            final AddressesApi.GetTransactionsResult result = new AddressesApi.GetTransactionsResult();
            result.setWasSuccess(false);
            result.setErrorMessage("Invalid address parameter: " + addressString);
            return new JsonResponse(Response.Codes.BAD_REQUEST, result);
        }

        try (final NodeJsonRpcConnection nodeJsonRpcConnection = environment.getNodeJsonRpcConnection()) {
            if (nodeJsonRpcConnection == null) {
                final AddressesApi.GetBalanceResult result = new AddressesApi.GetBalanceResult();
                result.setWasSuccess(false);
                result.setErrorMessage("Unable to connect to node.");
                return new JsonResponse(Response.Codes.SERVER_ERROR, result);
            }

            final Long balance;
            final Json addressJson;
            {
                final Json rpcResponseJson = nodeJsonRpcConnection.getAddressBalance(address);
                if (rpcResponseJson == null) {
                    return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                }

                if (! rpcResponseJson.getBoolean("wasSuccess")) {
                    final String errorMessage = rpcResponseJson.getString("errorMessage");
                    return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, errorMessage));
                }

                addressJson = rpcResponseJson.get("address");
                balance = rpcResponseJson.getLong("balance");
            }

            final AddressesApi.GetBalanceResult getTransactionResult = new AddressesApi.GetBalanceResult();
            getTransactionResult.setWasSuccess(true);
            getTransactionResult.setAddressJson(addressJson);
            getTransactionResult.setBalance(balance);
            return new JsonResponse(Response.Codes.OK, getTransactionResult);
        }
    }
}
