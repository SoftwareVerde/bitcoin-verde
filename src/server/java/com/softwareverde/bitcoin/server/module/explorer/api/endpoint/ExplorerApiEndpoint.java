package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.http.server.servlet.routed.RequestHandler;
import com.softwareverde.http.server.servlet.routed.json.JsonApplicationServlet;

import java.util.Map;


public abstract class ExplorerApiEndpoint extends JsonApplicationServlet<Environment> {
    public ExplorerApiEndpoint(final Environment environment) {
        super(environment);
    }

    @Override
    protected Response _handleRequest(final Environment environment, final Request request, final RequestHandler<Environment> requestHandler, final Map<String, String> routeParameters) throws Exception {
        final Response response = requestHandler.handleRequest(request, environment, routeParameters);
        response.addHeader("Access-Control-Allow-Origin", "*");
        return response;
    }
}
