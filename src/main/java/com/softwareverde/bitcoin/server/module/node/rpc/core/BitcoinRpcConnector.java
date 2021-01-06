package com.softwareverde.bitcoin.server.module.node.rpc.core;

import com.softwareverde.bitcoin.util.Util;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.util.Container;
import com.softwareverde.util.StringUtil;

public interface BitcoinRpcConnector {
    String getHost();
    Integer getPort();

    Monitor getMonitor();

    Response handleRequest(Request request, Monitor monitor);

    default Response handleRequest(Request request) {
        return this.handleRequest(request, null);
    }

    default Boolean isSuccessfulResponse(final Response response, final Json preParsedResponse, final Container<String> errorStringContainer) {
        errorStringContainer.value = null;
        if (response == null) { return false; }

        if (! Util.areEqual(Response.Codes.OK, response.getCode())) {
            return false;
        }

        final Json responseJson;
        if (preParsedResponse != null) {
            responseJson = preParsedResponse;
        }
        else {
            final String rawResponse = StringUtil.bytesToString(response.getContent());
            responseJson = Json.parse(rawResponse);
        }

        final String errorString = responseJson.getString("error");
        if (! Util.isBlank(errorString)) {
            errorStringContainer.value = errorString;
            return false;
        }

        return true;
    }
}
