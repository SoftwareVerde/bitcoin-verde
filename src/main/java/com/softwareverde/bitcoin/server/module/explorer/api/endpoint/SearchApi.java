package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.bitcoin.server.module.explorer.api.v1.get.SearchHandler;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;

public class SearchApi extends ExplorerApiEndpoint {
    public static class SearchResult extends ApiResult {
        public enum ObjectType {
            BLOCK, BLOCK_HEADER, TRANSACTION, ADDRESS, RAW_BLOCK, RAW_TRANSACTION
        }

        private ObjectType _objectType;
        public void setObjectType(final ObjectType objectType) { _objectType = objectType; }

        private Object _object;
        public void setObject(final Jsonable object) { _object = object; }
        public void setRawObject(final String rawObject) { _object = rawObject; }

        @Override
        public Json toJson() {
            final Json json = super.toJson();
            json.put("objectType", _objectType);
            json.put("object", _object);
            return json;
        }
    }

    public SearchApi(final String apiPrePath, final Environment environment) {
        super(environment);

        _defineEndpoint((apiPrePath + "/search"), HttpMethod.GET, new SearchHandler());
    }
}
