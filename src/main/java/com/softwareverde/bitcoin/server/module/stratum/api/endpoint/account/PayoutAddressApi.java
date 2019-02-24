package com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.miner.pool.AccountId;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiResult;
import com.softwareverde.bitcoin.server.module.stratum.database.AccountDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.servlet.AuthenticatedServlet;
import com.softwareverde.servlet.GetParameters;
import com.softwareverde.servlet.PostParameters;
import com.softwareverde.servlet.request.Request;
import com.softwareverde.servlet.response.JsonResponse;
import com.softwareverde.servlet.response.Response;

import static com.softwareverde.servlet.response.Response.ResponseCodes;

public class PayoutAddressApi extends AuthenticatedServlet {
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;

    public PayoutAddressApi(final Configuration.StratumProperties stratumProperties, final MysqlDatabaseConnectionFactory databaseConnectionFactory) {
        super(stratumProperties);
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    @Override
    protected Response _onAuthenticatedRequest(final AccountId accountId, final Request request) {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        if (request.getMethod() == Request.HttpMethod.GET) {
            try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                final AccountDatabaseManager accountDatabaseManager = new AccountDatabaseManager(databaseConnection);
                final Address address = accountDatabaseManager.getPayoutAddress(accountId);

                final StratumApiResult apiResult = new StratumApiResult(true, null);
                apiResult.put("address", address.toBase58CheckEncoded());
                return new JsonResponse(ResponseCodes.OK, apiResult);
            }
            catch (final DatabaseException exception) {
                Logger.log(exception);
                return new JsonResponse(ResponseCodes.SERVER_ERROR, new StratumApiResult(false, "An internal error occurred."));
            }
        }
        else if (request.getMethod() == Request.HttpMethod.POST) {
            final AddressInflater addressInflater = new AddressInflater();

            final String addressString = postParameters.get("address");
            final Address address;

            if (! addressString.isEmpty()) {
                address = addressInflater.fromBase58Check(addressString);
                if (address == null) {
                    return new JsonResponse(ResponseCodes.BAD_REQUEST, new StratumApiResult(false, "Invalid address."));
                }
            }
            else {
                address = null;
            }

            try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                final AccountDatabaseManager accountDatabaseManager = new AccountDatabaseManager(databaseConnection);
                accountDatabaseManager.setPayoutAddress(accountId, address);

                return new JsonResponse(ResponseCodes.OK, new StratumApiResult(true, null));
            }
            catch (final DatabaseException exception) {
                Logger.log(exception);
                return new JsonResponse(ResponseCodes.SERVER_ERROR, new StratumApiResult(false, "An internal error occurred."));
            }
        }
        else {
            return new JsonResponse(ResponseCodes.BAD_REQUEST, new StratumApiResult(false, "Invalid method."));
        }
    }
}
