package com.softwareverde.bitcoin.server.module.stratum.api.endpoint.pool;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiEndpoint;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiResult;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumDataHandler;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.json.Json;
import com.softwareverde.servlet.GetParameters;
import com.softwareverde.servlet.PostParameters;
import com.softwareverde.servlet.request.Request;
import com.softwareverde.servlet.response.JsonResponse;
import com.softwareverde.servlet.response.Response;

import static com.softwareverde.servlet.response.Response.ResponseCodes;

public class PoolPrototypeBlockApi extends StratumApiEndpoint {
    protected final StratumDataHandler _stratumDataHandler;

    public PoolPrototypeBlockApi(final Configuration.StratumProperties stratumProperties, final StratumDataHandler stratumDataHandler, final ThreadPool threadPool) {
        super(stratumProperties, threadPool);
        _stratumDataHandler = stratumDataHandler;
    }

    @Override
    protected Response _onRequest(final Request request) {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        if (request.getMethod() != Request.HttpMethod.GET) {
            return new JsonResponse(ResponseCodes.BAD_REQUEST, new StratumApiResult(false, "Invalid method."));
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
            return new JsonResponse(ResponseCodes.OK, apiResult);
        }
    }
}
