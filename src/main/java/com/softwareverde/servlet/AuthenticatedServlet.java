package com.softwareverde.servlet;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumApiEndpoint;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.http.cookie.Cookie;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.servlet.request.Request;
import com.softwareverde.servlet.response.Response;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;

import java.io.File;
import java.security.SecureRandom;
import java.util.List;

public abstract class AuthenticatedServlet extends StratumApiEndpoint {
    public AuthenticatedServlet(final Configuration.StratumProperties stratumProperties, final ThreadPool threadPool) {
        super(stratumProperties, threadPool);
    }

    protected static final String SESSION_COOKIE_KEY = "authentication_token";

    protected static String _getAuthenticationToken(final Request request) {
        final List<Cookie> cookies = request.getCookies();
        for (final Cookie cookie : cookies) {
            final String cookieKey = cookie.getKey();
            if (Util.areEqual(SESSION_COOKIE_KEY, cookieKey)) {
                final String authentication = Util.coalesce(cookie.getValue()).replaceAll("[^0-9A-Za-z]", "");
                if (authentication.length() != Sha256Hash.BYTE_COUNT) { return null; }

                Logger.log("Authentication Token: " + authentication);
                return authentication;
            }
        }

        return null;
    }

    protected Json _getSession(final Request request) {
        final String authenticationToken = _getAuthenticationToken(request);
        if (authenticationToken == null) { return null; }

        final String cookiesDirectory = (_stratumProperties.getCookiesDirectory() + "/");
        final String sessionData = StringUtil.bytesToString(IoUtil.getFileContents(cookiesDirectory + authenticationToken));
        if (sessionData.isEmpty()) { return null; }

        return Json.parse(sessionData);
    }

    protected void _newSession(final Request request, final Response response) {
        final SecureRandom secureRandom = new SecureRandom();
        final MutableByteArray authenticationToken = new MutableByteArray(Sha256Hash.BYTE_COUNT);
        secureRandom.nextBytes(authenticationToken.unwrap());

        final String sessionData = (new Json(false)).toString();

        final String cookiesDirectory = (_stratumProperties.getCookiesDirectory() + "/");
        IoUtil.putFileContents(cookiesDirectory + authenticationToken, StringUtil.stringToBytes(sessionData));

        final Cookie sessionCookie = new Cookie();
        {
            sessionCookie.setIsHttpOnly(true);
            sessionCookie.setDomain(request.resolveHostname());
            sessionCookie.setIsSameSiteStrict(true);
            sessionCookie.setIsSecure(true);
            sessionCookie.setPath("/");
            sessionCookie.setKey(SESSION_COOKIE_KEY);
            sessionCookie.setValue(authenticationToken.toString());
        }

        Logger.log("Set Cookie: " + authenticationToken.toString());
        response.addCookie(sessionCookie);
    }

    protected void _updateSession(final Request request, final Json session) {
        final String authenticationToken = _getAuthenticationToken(request);
        if (authenticationToken == null) { return; }

        final String cookiesDirectory = (_stratumProperties.getCookiesDirectory() + "/");
        final String sessionData = session.toString();
        IoUtil.putFileContents(cookiesDirectory + authenticationToken, StringUtil.stringToBytes(sessionData));
    }

    protected void _destroySession(final Request request, final Response response) {
        final String authenticationToken = _getAuthenticationToken(request);
        if (authenticationToken == null) { return; }

        final String cookiesDirectory = (_stratumProperties.getCookiesDirectory() + "/");
        try {
            final File file = new File(cookiesDirectory + authenticationToken);
            file.delete();
        }
        catch (final Exception exception) { }

        final Cookie sessionCookie = new Cookie();
        {
            sessionCookie.setKey(SESSION_COOKIE_KEY);
            sessionCookie.setValue("");
        }
        response.addCookie(sessionCookie);
    }
}
