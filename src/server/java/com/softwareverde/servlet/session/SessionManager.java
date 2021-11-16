package com.softwareverde.servlet.session;

import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.http.cookie.Cookie;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;

import java.io.File;
import java.security.SecureRandom;
import java.util.List;

public class SessionManager {
    public static final String SESSION_COOKIE_KEY = "authentication_token";

    public static Cookie createSecureCookie(final String key, final String value) {
        final Cookie cookie = new Cookie();
        cookie.setIsHttpOnly(true);
        cookie.setIsSameSiteStrict(true);
        cookie.setIsSecure(true);
        cookie.setPath("/");
        cookie.setKey(key);
        cookie.setValue(value);
        return cookie;
    }

    public static SessionId getSessionId(final Request request) {
        final List<Cookie> cookies = request.getCookies();
        for (final Cookie cookie : cookies) {
            final String cookieKey = cookie.getKey();
            if (Util.areEqual(SESSION_COOKIE_KEY, cookieKey)) {
                final String sessionId = Util.coalesce(cookie.getValue()).replaceAll("[^0-9A-Za-z]", "");
                return SessionId.wrap(sessionId);
            }
        }

        return null;
    }

    protected final String _cookiesDirectory;
    protected final Boolean _enableSecureCookies;

    public SessionManager(final String cookiesDirectory, final Boolean enableSecureCookies) {
        _cookiesDirectory = cookiesDirectory;
        _enableSecureCookies = enableSecureCookies;
    }

    public Session getSession(final Request request) {
        final SessionId sessionId = SessionManager.getSessionId(request);
        if (sessionId == null) { return null; }

        final String sessionData = StringUtil.bytesToString(IoUtil.getFileContents(_cookiesDirectory + sessionId));
        if (sessionData.isEmpty()) { return null; }

        return Session.newSession(sessionId, sessionData);
    }

    public Session createSession(final Request request, final Response response) {
        final SecureRandom secureRandom = new SecureRandom();
        final MutableByteArray authenticationToken = new MutableByteArray(Sha256Hash.BYTE_COUNT);
        secureRandom.nextBytes(authenticationToken.unwrap());

        final Session session = Session.newSession(SessionId.wrap(authenticationToken.toString()));
        final Json sessionData = session.getMutableData();

        IoUtil.putFileContents(_cookiesDirectory + session.getSessionId(), StringUtil.stringToBytes(sessionData.toString()));

        final Cookie sessionCookie = SessionManager.createSecureCookie(SESSION_COOKIE_KEY, authenticationToken.toString());
        if (! _enableSecureCookies) {
            sessionCookie.setIsSecure(false);
        }

        response.addCookie(sessionCookie);

        return session;
    }

    public void saveSession(final Session session) {
        IoUtil.putFileContents(_cookiesDirectory + session.getSessionId(), StringUtil.stringToBytes(session.toString()));
    }

    public void destroySession(final Request request, final Response response) {
        final SessionId sessionId = SessionManager.getSessionId(request);
        if (sessionId == null) { return; }

        try {
            final File file = new File(_cookiesDirectory + sessionId);
            file.delete();
        }
        catch (final Exception exception) { }

        final Cookie sessionCookie = SessionManager.createSecureCookie(SESSION_COOKIE_KEY, "");
        sessionCookie.setMaxAge(0, true);
        response.addCookie(sessionCookie);
    }
}