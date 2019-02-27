package com.softwareverde.bitcoin.server.module.proxy.webrequest;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.servlet.request.Request;
import com.softwareverde.servlet.socket.ConnectionLayer;
import com.softwareverde.servlet.socket.WebSocket;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.ReflectionUtil;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;

import javax.net.ssl.*;
import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class HttpRequest {
    public interface Callback {
        void run(HttpRequest request);
    }

    public interface WebSocketFactory {
        WebSocket newWebSocket(Socket socket);
    }

    public static class DefaultWebSocketFactory implements WebSocketFactory {
        private static final AtomicLong NEXT_WEB_SOCKET_ID = new AtomicLong(1L);

        @Override
        public WebSocket newWebSocket(final Socket socket) {
            final Long webSocketId = NEXT_WEB_SOCKET_ID.getAndIncrement();
            return new WebSocket(webSocketId, WebSocket.Mode.CLIENT, ConnectionLayer.newConnectionLayer(socket), WebSocket.DEFAULT_MAX_PACKET_BYTE_COUNT);
        }
    }

    public static boolean containsHeaderValue(final String key, final String value, final Map<String, List<String>> headers) {
        for (final String headerKey : headers.keySet()) {
            if (Util.areEqual(Util.coalesce(key).toLowerCase(), Util.coalesce(headerKey).toLowerCase())) {
                for (final String headerValue : headers.get(headerKey)) {
                    if (Util.areEqual(Util.coalesce(value).toLowerCase(), Util.coalesce(headerValue).toLowerCase())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static String getHeaderValue(final String key, final Map<String, List<String>> headers) {
        for (final String headerKey : headers.keySet()) {
            if (Util.areEqual(Util.coalesce(key).toLowerCase(), Util.coalesce(headerKey).toLowerCase())) {
                final List<String> headerValues = headers.get(headerKey);
                if (headerValues.isEmpty()) { continue; }
                return headerValues.get(0);
            }
        }
        return null;
    }

    public static boolean containsUpgradeToWebSocketHeader(final Map<String, List<String>> headers) {
        return containsHeaderValue("upgrade", "websocket", headers);
    }

    protected String _url;

    protected Request.HttpMethod _method = Request.HttpMethod.GET;
    protected List<String> _setCookies = new LinkedList<String>();
    protected Map<String, String> _setHeaders = new HashMap<String, String>();

    protected ByteArray _postData = new MutableByteArray(0);
    protected String _queryString = "";

    protected Map<String, List<String>> _headers = null;

    protected boolean _resultReady = false;
    protected ByteArray _rawResult;
    protected Integer _responseCode;
    protected boolean _followsRedirects = false;
    protected int _redirectCount = 0;
    protected int _maxRedirectCount = 10;

    protected boolean _allowWebSocketUpgrade = false;
    protected WebSocket _webSocket = null;
    protected WebSocketFactory _webSocketFactory = new DefaultWebSocketFactory();

    // NOTE: Handles both android-formatted and ios-formatted cookie strings.
    //  iOS concatenates their cookies into one string, delimited by commas;
    //  Android cookies are separate cookie-records.
    protected List<String> _parseCookies(final String cookie) {
        final List<String> cookies = new LinkedList<String>();

        if (cookie.contains(";")) {
            Boolean skipNext = false;
            for (final String cookieSegment : cookie.replaceAll(",", ";").split(";")) {
                if (skipNext) {
                    skipNext = false;
                    continue;
                }

                final String cleanedCookie = cookieSegment.trim();

                if (cleanedCookie.toLowerCase().contains("expires=")) {
                    skipNext = true;
                    continue;
                }
                if (cleanedCookie.toLowerCase().contains("max-age=")) {
                    continue;
                }
                if (cleanedCookie.toLowerCase().contains("path=")) {
                    continue;
                }
                if (cleanedCookie.toLowerCase().contains("httponly")) {
                    continue;
                }

                cookies.add(cleanedCookie);
            }
        }
        else {
            cookies.add(cookie.trim());
        }

        return cookies;
    }

    public HttpRequest() { }

    public void setUrl(String url) {
        _url = url;
    }

    public String getUrl() { return _url; }

    public void setCookie(String cookie) {
        if (cookie.contains(";")) {
            cookie = cookie.substring(0, cookie.indexOf(";"));
        }
        _setCookies.add(cookie);
    }
    public void setHeader(final String key, final String value) {
        _setHeaders.put(key, value);
    }

    public void setFollowsRedirects(final boolean followsRedirects) {
        _followsRedirects = followsRedirects;
    }

    public void setMethod(final Request.HttpMethod method) {
        _method = method;
    }

    public Request.HttpMethod getMethod() {
        return _method;
    }

    public boolean hasResult() {
        return _resultReady;
    }
    public Integer getResponseCode() { return _responseCode; }

    public synchronized Json getJsonResult() {
        if (! _resultReady) return null;

        return Json.parse(StringUtil.bytesToString(_rawResult.getBytes()));
    }
    public synchronized ByteArray getRawResult() { return _rawResult; }

    protected synchronized void _setResult(final ByteArray result) {
        _rawResult = result;
        _resultReady = true;
    }

    public Map<String, List<String>> getHeaders() {
        if (! _resultReady) return null;

        return _headers;
    }

    public List<String> getCookies() {
        if (! _resultReady) return null;
        if (_headers.containsKey("Set-Cookie")) {
            List<String> cookies = new LinkedList<String>();
            for (final String cookie : _headers.get("Set-Cookie")) {
                cookies.addAll(_parseCookies(cookie));
            }

            return cookies;
        }

        return new LinkedList<String>();
    }

    public void setQueryString(final String queryString) {
        _queryString = Util.coalesce(queryString);
    }

    public void setPostData(final ByteArray byteArray) {
        if (byteArray != null) {
            _postData = byteArray.asConst();
        }
        else {
            _postData = new MutableByteArray(0);
        }
    }

    public void setAllowWebSocketUpgrade(final boolean allowWebSocketUpgrade) {
        _allowWebSocketUpgrade = allowWebSocketUpgrade;
    }

    public void setWebSocketFactory(final WebSocketFactory webSocketFactory) {
        _webSocketFactory = webSocketFactory;
    }

    public boolean allowsWebSocketUpgrade() {
        return _allowWebSocketUpgrade;
    }

    public boolean didUpgradeToWebSocket() {
        return (_webSocket != null);
    }

    public void execute(boolean nonblocking) {
        this.execute(nonblocking, null);
    }

    public void execute(boolean nonblocking, Callback callback) {
        this._resultReady = false;
        this._rawResult = null;

        if (_url != null) {
            final HttpRequestExecutionThread thread = new HttpRequestExecutionThread(this, callback);
            if (nonblocking) {
                thread.start();
            }
            else {
                thread.run();
            }
        }
    }

    public WebSocket getWebSocket() {
        return _webSocket;
    }

    @SuppressWarnings("unused")
    protected static final Class<?>[] unused = {
        HttpsURLConnection.class
    };
}

class HttpRequestExecutionThread extends Thread {
    protected HttpRequest _httpRequest;
    protected HttpRequest.Callback _callback;

    public HttpRequestExecutionThread(final HttpRequest httpRequest, final HttpRequest.Callback callback) {
        _httpRequest = httpRequest;
        _callback = callback;
    }

    public void run() {
        if (_httpRequest._url == null) return;

        try {
            final String urlString = _httpRequest._url;
            final URL url = new URL((urlString) + (urlString.contains("?") ? "" : "?") + _httpRequest._queryString);

            final HttpURLConnection connection = (HttpURLConnection) (url.openConnection());

//            if (connection instanceof HttpsURLConnection) {
//                final HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
//                httpsConnection.setHostnameVerifier(new HostnameVerifier() {
//                    @Override
//                    public boolean verify(final String hostname, final SSLSession sslSession) {
//                        return true; // HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, sslSession);
//                    }
//                });
//
//                final X509TrustManager trustManager = new X509TrustManager() {
//                    @Override
//                    public void checkClientTrusted(final X509Certificate[] x509Certificates, final String s) throws CertificateException { }
//
//                    @Override
//                    public void checkServerTrusted(final X509Certificate[] x509Certificates, final String s) throws CertificateException { }
//
//                    @Override
//                    public X509Certificate[] getAcceptedIssuers() {
//                        return new X509Certificate[0];
//                    }
//                };
//
//                final SSLContext sslContext = SSLContext.getInstance("TLS");
//                sslContext.init(null, new X509TrustManager[]{ trustManager }, new SecureRandom());
//                final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
//                httpsConnection.setSSLSocketFactory(sslSocketFactory);
//            }

            connection.setInstanceFollowRedirects(_httpRequest._followsRedirects);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            final StringBuilder cookies = new StringBuilder();
            {
                String separator = "";
                for (final String cookie : _httpRequest._setCookies) {
                    cookies.append(separator);
                    cookies.append(cookie);
                    separator = "; ";
                }
            }
            connection.setRequestProperty("Cookie", cookies.toString());

            for (final String key : _httpRequest._setHeaders.keySet()) {
                final String value = _httpRequest._setHeaders.get(key);
                connection.setRequestProperty(key, value);
            }

            connection.setRequestMethod(_httpRequest._method.name());

            if (_httpRequest.getMethod() == Request.HttpMethod.POST) {
                connection.setDoOutput(true);

                try (final DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
                    out.write(_httpRequest._postData.getBytes());
                    out.flush();
                }
            }

            connection.connect();

            final int responseCode = connection.getResponseCode();
            _httpRequest._responseCode = responseCode;

            final Map<String, List<String>> responseHeaders = connection.getHeaderFields();
            _httpRequest._headers = responseHeaders;

            // HttpURLConnection will not handle redirection from http to https, so it is is handled here...
            //  NOTE: Protocol switches will not be handled, except for http to https.  Downgrades from https to http will not be followed.
            if ( (_httpRequest._followsRedirects) && (_httpRequest._redirectCount < _httpRequest._maxRedirectCount) ) {
                if (responseCode >= 300 && responseCode < 400) {
                    final String newLocation = HttpRequest.getHeaderValue("location", responseHeaders);
                    if (newLocation != null) {
                        final boolean isHttpBase = ( (_httpRequest._url.startsWith("http") && (newLocation.startsWith("http"))) );
                        final boolean isHttpDowngrade = ( (_httpRequest._url.startsWith("https")) && (! newLocation.startsWith("https")) );
                        if ( (isHttpBase) && (! isHttpDowngrade) ) {
                            _httpRequest._url = newLocation;
                            _httpRequest._redirectCount += 1;
                            connection.disconnect();

                            (new HttpRequestExecutionThread(_httpRequest, _callback)).run();
                            return;
                        }
                    }
                }
            }

            final boolean upgradeToWebSocket = (_httpRequest.allowsWebSocketUpgrade() && HttpRequest.containsUpgradeToWebSocketHeader(responseHeaders));

            if (! upgradeToWebSocket) {
                if (_httpRequest._responseCode >= 400) {
                    final ByteArray data = MutableByteArray.wrap(IoUtil.readStreamOrThrow(connection.getErrorStream()));
                    _httpRequest._setResult(data);
                }
                else {
                    final ByteArray data = MutableByteArray.wrap(IoUtil.readStreamOrThrow(connection.getInputStream()));
                    _httpRequest._setResult(data);
                }

                // Close Connection
                connection.disconnect();
            }
            else {
                try {
                    final Object httpClient = ReflectionUtil.getValue(connection, "http");
                    final Socket socket = ReflectionUtil.getValue(httpClient, "serverSocket");

                    _httpRequest._webSocket = _httpRequest._webSocketFactory.newWebSocket(socket);
                }
                catch (final Exception exception) {
                    Logger.log("Unable to get underlying socket for WebSocket within HttpRequest.");
                    connection.disconnect();
                }
            }
        }
        catch (final Exception exception) {
            Logger.log(exception);
        }

        if (_callback != null) {
            _callback.run(_httpRequest);
        }
    }
}