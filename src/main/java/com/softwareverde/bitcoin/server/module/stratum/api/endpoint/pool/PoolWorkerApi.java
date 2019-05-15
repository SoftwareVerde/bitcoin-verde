package com.softwareverde.bitcoin.server.module.stratum.api.endpoint.pool;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiEndpoint;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiResult;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumDataHandler;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.querystring.PostParameters;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;

public class PoolWorkerApi extends StratumApiEndpoint {
    protected final StratumDataHandler _stratumDataHandler;

    public PoolWorkerApi(final Configuration.StratumProperties stratumProperties, final StratumDataHandler stratumDataHandler) {
        super(stratumProperties);
        _stratumDataHandler = stratumDataHandler;
    }

    @Override
    protected Response _onRequest(final Request request) {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        {   // GET PROTOTYPE BLOCK
            // Requires GET:
            // Requires POST:

            final Block prototypeBlock = _stratumDataHandler.getPrototypeBlock();
            final Json prototypeBlockJson = prototypeBlock.toJson();

            final StratumApiResult prototypeBlockResult = new StratumApiResult();
            prototypeBlockResult.setWasSuccess(true);
            prototypeBlockResult.put("block", prototypeBlockJson);
            return new JsonResponse(Response.Codes.OK, prototypeBlockResult);
        }
    }
}
