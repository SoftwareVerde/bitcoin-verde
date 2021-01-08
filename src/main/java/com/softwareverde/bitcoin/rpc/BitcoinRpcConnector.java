package com.softwareverde.bitcoin.rpc;

import com.softwareverde.bitcoin.rpc.monitor.Monitor;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;

public interface BitcoinRpcConnector {
    String getHost();
    Integer getPort();

    Monitor getMonitor();

    Response handleRequest(Request request, Monitor monitor);

    default Response handleRequest(Request request) {
        return this.handleRequest(request, null);
    }
}
