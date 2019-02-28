package com.softwareverde.bitcoin.server.module.proxy;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.proxy.webrequest.HttpRequest;
import com.softwareverde.bitcoin.server.module.proxy.webrequest.WebRequest;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.StringUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.httpserver.HttpServer;
import com.softwareverde.servlet.GetParameters;
import com.softwareverde.servlet.WebSocketEndpoint;
import com.softwareverde.servlet.WebSocketServlet;
import com.softwareverde.servlet.request.Headers;
import com.softwareverde.servlet.request.WebSocketRequest;
import com.softwareverde.servlet.response.WebSocketResponse;
import com.softwareverde.servlet.socket.WebSocket;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProxyModule {
    public static void execute(final String configurationFileName) {
        final ProxyModule stratumModule = new ProxyModule(configurationFileName);
        stratumModule.loop();
    }

    public static class ProxiedEndpoint {
        public final String protocol;
        public final String hostname;
        public final Integer port;

        public ProxiedEndpoint(final String protocol, final String hostname, final Integer port) {
            this.protocol = protocol;
            this.hostname = hostname;
            this.port = port;
        }
    }

    protected void _printError(final String errorMessage) {
        System.err.println(errorMessage);
    }

    protected final SystemTime _systemTime = new SystemTime();

    protected final Configuration _configuration;
    protected final HttpServer _apiServer = new HttpServer();

    protected HashMap<Long, WebSocket> _proxiedWebsockets = new HashMap<Long, WebSocket>();

    protected Configuration _loadConfigurationFile(final String configurationFilename) {
        final File configurationFile = new File(configurationFilename);
        if (!configurationFile.isFile()) {
            _printError("Invalid configuration file.");
            BitcoinUtil.exitFailure();
        }

        return new Configuration(configurationFile);
    }

    protected boolean _isWebSocketHeader(final String headerKey, final String headerValue) {
        return (Util.areEqual("upgrade", Util.coalesce(headerKey).toLowerCase()) && Util.areEqual("websocket", Util.coalesce(headerValue).toLowerCase()));
    }

    public ProxyModule(final String configurationFilename) {
        _configuration = _loadConfigurationFile(configurationFilename);

        final Configuration.ProxyProperties proxyProperties = _configuration.getProxyProperties();
        final Configuration.StratumProperties stratumProperties = _configuration.getStratumProperties();
        final Configuration.ExplorerProperties explorerProperties = _configuration.getExplorerProperties();

        final String tlsKeyFile = proxyProperties.getTlsKeyFile();
        final String tlsCertificateFile = proxyProperties.getTlsCertificateFile();
        if ( (tlsKeyFile != null) && (tlsCertificateFile != null) ) {
            _apiServer.setTlsPort(proxyProperties.getTlsPort());
            _apiServer.setCertificate(proxyProperties.getTlsCertificateFile(), proxyProperties.getTlsKeyFile());
            _apiServer.enableEncryption(true);
            _apiServer.redirectToTls(true, proxyProperties.getExternalTlsPort());
        }

        _apiServer.setPort(proxyProperties.getHttpPort());

        final HashMap<String, ProxiedEndpoint> proxyConfiguration = new HashMap<String, ProxiedEndpoint>();
        proxyConfiguration.put("^pool\\..*$", new ProxiedEndpoint("http", "localhost", stratumProperties.getHttpPort()));
        proxyConfiguration.put(".*", new ProxiedEndpoint("http", "localhost", explorerProperties.getPort()));

        final WebSocketEndpoint endpoint = new WebSocketEndpoint(new WebSocketServlet() {
            @Override
            public WebSocketResponse onRequest(final WebSocketRequest request) {
                final HttpRequest httpRequest = new HttpRequest();

                final String serverHostname;
                {
                    final Headers headers = request.getHeaders();
                    if (headers.containsHeader("host")) {
                        serverHostname = Util.coalesce(headers.getHeader("host").get(0));
                    }
                    else {
                        serverHostname = request.resolveHostname();
                    }
                }

                String url = null;
                for (final String hostnameRegex : proxyConfiguration.keySet()) {
                    final Pattern pattern = Pattern.compile(hostnameRegex);
                    final Matcher matcher = pattern.matcher(serverHostname);
                    if (matcher.matches()) {
                        final ProxiedEndpoint proxiedEndpoint = proxyConfiguration.get(hostnameRegex);
                        url = (proxiedEndpoint.protocol + "://" + proxiedEndpoint.hostname + ":" + proxiedEndpoint.port);
                        break;
                    }
                }
                httpRequest.setUrl(url + request.getFilePath());

                httpRequest.setMethod(request.getMethod());
                httpRequest.setFollowsRedirects(true);
                httpRequest.setValidateSslCertificates(false);

                Boolean isWebSocketRequest = false;
                final Headers headers = request.getHeaders();
                for (final String headerKey : headers.getHeaderNames()) {
                    String separator = "";
                    final StringBuilder headerBuilder = new StringBuilder();
                    for (final String headerValue : headers.getHeader(headerKey)) {
                        headerBuilder.append(separator);
                        headerBuilder.append(headerValue);
                        separator = "; ";

                        if (_isWebSocketHeader(headerKey, headerValue)) {
                            isWebSocketRequest = true;
                        }
                    }
                    httpRequest.setHeader(headerKey, headerBuilder.toString());
                }

                final GetParameters getParameters = request.getGetParameters();
                final ByteArray postData = MutableByteArray.wrap(request.getRawPostData());
                httpRequest.setQueryString(StringUtil.bytesToString(WebRequest.buildQueryString(getParameters).unwrap()));
                httpRequest.setPostData(postData);

                if (isWebSocketRequest) {
                    httpRequest.setAllowWebSocketUpgrade(true);
                }

                httpRequest.execute(false);

                final WebSocketResponse response = new WebSocketResponse();
                response.setCode(httpRequest.getResponseCode());

                final Map<String, List<String>> proxiedHeaders = httpRequest.getHeaders();
                if (proxiedHeaders != null) {
                    for (final String headerKey : proxiedHeaders.keySet()) {
                        for (final String headerValue : proxiedHeaders.get(headerKey)) {
                            if (headerKey != null) {
                                response.addHeader(headerKey, headerValue);
                            }
                        }
                    }
                }

                if (httpRequest.didUpgradeToWebSocket()) {
                    final WebSocket webSocket = httpRequest.getWebSocket();
                    final Long webSocketId = webSocket.getId();

                    _proxiedWebsockets.put(webSocketId, webSocket);

                    response.setWebSocketId(webSocketId);
                    response.upgradeToWebSocket();
                }
                else {
                    final ByteArray rawResult = httpRequest.getRawResult();
                    response.setContent((rawResult != null ? rawResult.getBytes() : new byte[0]));
                }

                return response;
            }

            @Override
            public void onNewWebSocket(final WebSocket externalWebSocket) {
                final WebSocket proxiedWebSocket = _proxiedWebsockets.get(externalWebSocket.getId());
                if (proxiedWebSocket == null) {
                    externalWebSocket.close();
                    return;
                }

                proxiedWebSocket.setMessageReceivedCallback(new WebSocket.MessageReceivedCallback() {
                    @Override
                    public void onMessage(final String message) {
                        externalWebSocket.sendMessage(message);
                    }
                });

                proxiedWebSocket.setBinaryMessageReceivedCallback(new WebSocket.BinaryMessageReceivedCallback() {
                    @Override
                    public void onMessage(final byte[] bytes) {
                        externalWebSocket.sendMessage(bytes);
                    }
                });

                proxiedWebSocket.setConnectionClosedCallback(new WebSocket.ConnectionClosedCallback() {
                    @Override
                    public void onClose(final int code, final String message) {
                        externalWebSocket.close();
                    }
                });

                externalWebSocket.setMessageReceivedCallback(new WebSocket.MessageReceivedCallback() {
                    @Override
                    public void onMessage(final String message) {
                        proxiedWebSocket.sendMessage(message);
                    }
                });

                externalWebSocket.setBinaryMessageReceivedCallback(new WebSocket.BinaryMessageReceivedCallback() {
                    @Override
                    public void onMessage(final byte[] bytes) {
                        proxiedWebSocket.sendMessage(bytes);
                    }
                });

                externalWebSocket.setConnectionClosedCallback(new WebSocket.ConnectionClosedCallback() {
                    @Override
                    public void onClose(final int code, final String message) {
                        proxiedWebSocket.close();
                    }
                });

                proxiedWebSocket.startListening();
                externalWebSocket.startListening();
            }
        });

        endpoint.setStrictPathEnabled(false);
        endpoint.setPath("/");
        _apiServer.addEndpoint(endpoint);
    }

    public void loop() {
        _apiServer.start();

        while (true) {
            try { Thread.sleep(60000L); } catch (final Exception exception) { break; }
        }

        _apiServer.stop();
    }
}