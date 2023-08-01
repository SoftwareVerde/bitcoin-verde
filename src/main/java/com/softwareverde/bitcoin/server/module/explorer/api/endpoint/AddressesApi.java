package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.bitcoin.server.module.explorer.api.v1.get.GetAddressBalanceHandler;
import com.softwareverde.bitcoin.server.module.explorer.api.v1.get.GetAddressTransactionsHandler;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.json.Json;

public class AddressesApi extends ExplorerApiEndpoint {
    public static class GetBalanceResult extends ApiResult {
        private Long _balance;
        private Json _addressJson;

        public void setBalance(final Long balance) {
            _balance = balance;
        }

        public void setAddressJson(final Json addressJson) {
            _addressJson = addressJson;
        }

        @Override
        public Json toJson() {
            final Json json = super.toJson();
            json.put("balance", _balance);
            json.put("address", _addressJson);
            return json;
        }
    }

    public static class GetTransactionsResult extends GetBalanceResult {
        private Json _transactionsJson;

        public void setTransactionsJson(final Json transactionArrayJson) {
            _transactionsJson = transactionArrayJson;
        }

        @Override
        public Json toJson() {
            final Json json = super.toJson();
            json.put("transactions", _transactionsJson);
            return json;
        }
    }

    public AddressesApi(final String apiPrePath, final Environment environment) {
        super(environment);

        _defineEndpoint((apiPrePath + "/addresses/<address>/balance"), HttpMethod.GET, new GetAddressBalanceHandler());
        _defineEndpoint((apiPrePath + "/addresses/<address>/transactions"), HttpMethod.GET, new GetAddressTransactionsHandler());
    }
}
