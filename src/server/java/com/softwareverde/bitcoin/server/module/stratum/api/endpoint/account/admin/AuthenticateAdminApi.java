package com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account.admin;

import com.softwareverde.bitcoin.server.configuration.StratumProperties;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiEndpoint;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiResult;
import com.softwareverde.bitcoin.server.properties.PropertiesStore;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.pbkdf2.Pbkdf2Key;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.querystring.PostParameters;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.servlet.session.Session;
import com.softwareverde.util.Util;

public class AuthenticateAdminApi extends StratumApiEndpoint {
    protected static final String ADMIN_PASSWORD_HASH_KEY = "admin_password_hash";
    protected static final String DEFAULT_ADMIN_PASSWORD = "admin";

    public static Boolean isAdminPasswordSet(final PropertiesStore propertiesStore) {
        final String adminPasswordHash = propertiesStore.getString(AuthenticateAdminApi.ADMIN_PASSWORD_HASH_KEY);
        return (! Util.isBlank(adminPasswordHash));
    }

    public static void setDefaultAdminPassword(final PropertiesStore propertiesStore) {
        final Pbkdf2Key pbkdf2Key = new Pbkdf2Key(AuthenticateAdminApi.DEFAULT_ADMIN_PASSWORD);
        final String serializedPbkdf2Key = (pbkdf2Key.getKey() + ":" + pbkdf2Key.getSalt() + ":" + pbkdf2Key.getIterations());
        propertiesStore.set(AuthenticateAdminApi.ADMIN_PASSWORD_HASH_KEY, serializedPbkdf2Key);
    }

    public static Boolean validatePassword(final String password, final PropertiesStore propertiesStore) {
        final String passwordHashParameters = propertiesStore.getString(AuthenticateAdminApi.ADMIN_PASSWORD_HASH_KEY);
        final String[] passwordHashParametersParts = passwordHashParameters.split(":");
        if (passwordHashParametersParts.length != 3) { return false; }

        final ByteArray passwordKey = ByteArray.fromHexString(passwordHashParametersParts[0]);
        final ByteArray salt = ByteArray.fromHexString(passwordHashParametersParts[1]);
        final Integer iterations = Util.parseInt(passwordHashParametersParts[2]);
        final Integer keyBitCount = (passwordKey.getByteCount() * 8);

        final Pbkdf2Key providedPbkdf2Key = new Pbkdf2Key(password, iterations, salt, keyBitCount);
        final ByteArray providedKey = providedPbkdf2Key.getKey();

        if (! Util.areEqual(passwordKey, providedKey)) { return false; }

        return true;
    }

    public static final Integer MIN_PASSWORD_LENGTH = 8;

    protected final PropertiesStore _propertiesStore;

    public AuthenticateAdminApi(final StratumProperties stratumProperties, final PropertiesStore propertiesStore) {
        super(stratumProperties);

        _propertiesStore = propertiesStore;
    }

    @Override
    protected Response _onRequest(final Request request) {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        if (request.getMethod() != HttpMethod.POST) {
            return new JsonResponse(Response.Codes.BAD_REQUEST, new StratumApiResult(false, "Invalid method."));
        }

        {   // AUTHENTICATE
            // Requires GET:
            // Requires POST: email, password

            final String email = postParameters.get("email");
            if (email.isEmpty()) {
                return new JsonResponse(Response.Codes.BAD_REQUEST, new StratumApiResult(false, "Invalid email address."));
            }

            final String password = postParameters.get("password");

            final Boolean isAuthenticated = AuthenticateAdminApi.validatePassword(password, _propertiesStore);
            if (! isAuthenticated) {
                return new JsonResponse(Response.Codes.NOT_AUTHORIZED, new StratumApiResult(false, "Not authorized."));
            }

            final Response response = new JsonResponse(Response.Codes.OK, new StratumApiResult(true, null));
            final Session session = _sessionManager.createSession(request, response);
            final Json sessionData = session.getMutableData();
            sessionData.put("isAdmin", true);
            _sessionManager.saveSession(session);

            return response;
        }
    }
}
