package com.softwareverde.bitcoin.rpc.core;

import com.softwareverde.http.HttpMethod;
import com.softwareverde.http.form.MultiPartFormData;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.querystring.PostParameters;
import com.softwareverde.http.server.servlet.request.Request;

import java.util.ArrayList;

public class MutableRequest extends Request {
    public MutableRequest() { }

    protected void setHostname(final HostNameLookup hostname) {
        _hostname = hostname;
    }

    protected void setFilePath(final String filePath) {
        _filePath = filePath;
    }


    protected void setGetParameters(final GetParameters getParameters) {
        _getParameters = getParameters;
    }

    protected void setPostParameters(final PostParameters postParameters) {
        _postParameters = postParameters;
    }

    protected void setMultiPartFormData(final MultiPartFormData multiPartFormData) {
        _multiPartFormData = multiPartFormData;
    }

    protected void setRawQueryString(final String rawQueryString) {
        _rawQueryString = rawQueryString;
    }

    public void setHeader(final String key, final String value) {
        final java.util.List<String> values = new ArrayList<>(1);
        values.add(value);

        _headers.setHeader(key, values); // Overwrite any existing values.
    }

    public void setMethod(final HttpMethod method) {
        _method = method;
    }

    public void setRawPostData(final byte[] rawPostData) {
        _rawPostData = rawPostData;
    }
}
