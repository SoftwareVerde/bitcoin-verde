package com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account.admin;

import com.softwareverde.bitcoin.server.configuration.StratumProperties;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiEndpoint;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiResult;
import com.softwareverde.bitcoin.server.properties.PropertiesStore;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.querystring.PostParameters;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.servlet.session.Session;
import com.softwareverde.util.Util;

public class WorkerDifficultyApi extends StratumApiEndpoint {
    public static final String SHARE_DIFFICULTY_KEY = "default_share_difficulty";

    public static Boolean isShareDifficultySet(final PropertiesStore propertiesStore) {
        final Long shareDifficulty = propertiesStore.getLong(WorkerDifficultyApi.SHARE_DIFFICULTY_KEY);
        return (Util.coalesce(shareDifficulty) >= 2048L);
    }

    public static Long getShareDifficulty(final PropertiesStore propertiesStore) {
        return propertiesStore.getLong(WorkerDifficultyApi.SHARE_DIFFICULTY_KEY);
    }

    public static void setShareDifficulty(final Long shareDifficulty, final PropertiesStore propertiesStore) {
        propertiesStore.set(WorkerDifficultyApi.SHARE_DIFFICULTY_KEY, shareDifficulty);
    }

    protected final PropertiesStore _propertiesStore;
    protected Runnable _shareDifficultyUpdatedCallback;

    public WorkerDifficultyApi(final StratumProperties stratumProperties, final PropertiesStore propertiesStore) {
        super(stratumProperties);
        _propertiesStore = propertiesStore;
    }

    public void setShareDifficultyUpdatedCallback(final Runnable runnable) {
        _shareDifficultyUpdatedCallback = runnable;
    }

    @Override
    protected Response _onRequest(final Request request) {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        final HttpMethod httpMethod = request.getMethod();
        if (httpMethod == HttpMethod.GET) {
            // GET MINIMUM SHARE DIFFICULTY
            // Requires GET:

            final Long shareDifficulty = WorkerDifficultyApi.getShareDifficulty(_propertiesStore);

            final StratumApiResult apiResult = new StratumApiResult();
            apiResult.setWasSuccess(true);
            apiResult.put("shareDifficulty", shareDifficulty);
            return new JsonResponse(Response.Codes.OK, apiResult);
        }
        else if (httpMethod == HttpMethod.POST) {
            // SET MINIMUM SHARE DIFFICULTY
            // Requires POST: shareDifficulty

            final Session session = _sessionManager.getSession(request);
            if (session == null) {
                return new JsonResponse(Response.Codes.NOT_AUTHORIZED, new StratumApiResult(false, "Not authorized."));
            }

            final Json sessionJson = session.getMutableData();
            if (! sessionJson.getBoolean("isAdmin")) {
                return new JsonResponse(Response.Codes.NOT_AUTHORIZED, new StratumApiResult(false, "Not authorized."));
            }

            final Long shareDifficulty = Util.parseLong(postParameters.get("shareDifficulty"));
            if (shareDifficulty < 2048L) {
                return new JsonResponse(Response.Codes.BAD_REQUEST, new StratumApiResult(false, "Share difficulty must not be less than 2048."));
            }

            WorkerDifficultyApi.setShareDifficulty(shareDifficulty, _propertiesStore);
            final Runnable shareDifficultyUpdatedCallback = _shareDifficultyUpdatedCallback;
            if (shareDifficultyUpdatedCallback != null) {
                shareDifficultyUpdatedCallback.run();
            }

            final StratumApiResult apiResult = new StratumApiResult();
            apiResult.setWasSuccess(true);
            return new JsonResponse(Response.Codes.OK, apiResult);
        }
        else {
            return new JsonResponse(Response.Codes.BAD_REQUEST, new StratumApiResult(false, "Invalid method."));
        }
    }
}
