package com.softwareverde.bitcoin.server.module.stratum.api.endpoint.pool;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.configuration.StratumProperties;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiEndpoint;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiResult;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumDataHandler;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.querystring.PostParameters;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;

public class PoolPrototypeBlockApi extends StratumApiEndpoint {
    protected final StratumDataHandler _stratumDataHandler;

    public PoolPrototypeBlockApi(final StratumProperties stratumProperties, final StratumDataHandler stratumDataHandler) {
        super(stratumProperties);
        _stratumDataHandler = stratumDataHandler;
    }

    @Override
    protected Response _onRequest(final Request request) {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        if (request.getMethod() != HttpMethod.GET) {
            return new JsonResponse(Response.Codes.BAD_REQUEST, new StratumApiResult(false, "Invalid method."));
        }

        {   // GET PROTOTYPE BLOCK
            // Requires GET:
            // Requires POST:

            final Block prototypeBlock = _stratumDataHandler.getPrototypeBlock();
            final Json prototypeBlockJson = prototypeBlock.toJson();
            prototypeBlockJson.put("height", _stratumDataHandler.getPrototypeBlockHeight());

            final StratumApiResult apiResult = new StratumApiResult();
            apiResult.setWasSuccess(true);
            apiResult.put("block", prototypeBlockJson);
            return new JsonResponse(Response.Codes.OK, apiResult);
        }
    }
}
