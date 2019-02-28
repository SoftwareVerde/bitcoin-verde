package com.softwareverde.servlet.session;

import com.softwareverde.json.Json;

public class Session {
    public static Session newSession(final SessionId sessionId) {
        if (sessionId == null) { return null; }
        return new Session(sessionId);
    }

    public static Session newSession(final SessionId sessionId, final String sessionData) {
        if (sessionId == null) { return null; }
        return new Session(sessionId, sessionData);
    }

    protected final SessionId _sessionId;
    protected final Json _data;

    protected Session(final SessionId sessionId) {
        _sessionId = sessionId;
        _data = new Json(false);
    }

    protected Session(final SessionId sessionId, final String sessionData) {
        _sessionId = sessionId;
        _data = Json.parse(sessionData);
    }

    public SessionId getSessionId() {
        return _sessionId;
    }

    public Json getMutableData() {
        return _data;
    }

    @Override
    public String toString() {
        return _data.toString();
    }
}
