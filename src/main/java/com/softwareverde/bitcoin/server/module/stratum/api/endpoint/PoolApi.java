package com.softwareverde.bitcoin.server.module.stratum.api.endpoint;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.json.Json;
import com.softwareverde.servlet.GetParameters;
import com.softwareverde.servlet.PostParameters;
import com.softwareverde.servlet.request.Request;
import com.softwareverde.servlet.response.JsonResponse;
import com.softwareverde.servlet.response.Response;

import static com.softwareverde.servlet.response.Response.ResponseCodes;

public class PoolApi extends StratumApiEndpoint {
    private static class PrototypeBlockResult extends ApiResult {
        private Json _blockJson = new Json();

        public void setBlockJson(final Json blockJson) {
            _blockJson = blockJson;
        }

        @Override
        public Json toJson() {
            final Json json = super.toJson();
            json.put("block", _blockJson);
            return json;
        }
    }

    public PoolApi(final Configuration.StratumProperties stratumProperties, final StratumDataHandler stratumDataHandler, final ThreadPool threadPool) {
        super(stratumProperties, stratumDataHandler, threadPool);
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

            final PrototypeBlockResult prototypeBlockResult = new PrototypeBlockResult();
            prototypeBlockResult.setWasSuccess(true);
            prototypeBlockResult.setBlockJson(prototypeBlockJson);
            return new JsonResponse(ResponseCodes.OK, prototypeBlockResult);
        }
    }
}
