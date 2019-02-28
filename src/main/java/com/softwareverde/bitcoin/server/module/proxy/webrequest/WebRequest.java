package com.softwareverde.bitcoin.server.module.proxy.webrequest;

import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.io.Logger;
import com.softwareverde.servlet.querystring.QueryString;
import com.softwareverde.servlet.request.Request;
import com.softwareverde.util.StringUtil;

import java.net.URLEncoder;
import java.util.*;

public class WebRequest extends HttpRequest {
    public static MutableByteArray buildQueryString(final QueryString queryString) {
        final HashMap<String, String> params = new HashMap<String, String>();
        final HashMap<String, List<String>> arrayParams = new HashMap<String, List<String>>();

        for (final String key : queryString.getKeys()) {
            if (queryString.isArray(key)) {
                arrayParams.put(key, Arrays.asList(queryString.getArray(key)));
            }
            else {
                params.put(key, queryString.get(key));
            }
        }

        return WebRequest.buildQueryString(params, arrayParams);
    }

    public static MutableByteArray buildQueryString(final Map<String, String> params, final Map<String, List<String>> arrayParams) {
        final StringBuilder postQueryStringBuilder = new StringBuilder();
        String separator = "";
        try {
            for (String key : params.keySet()) {
                String value = params.get(key);

                if (value == null) {
                    continue;
                }

                postQueryStringBuilder.append(separator);
                postQueryStringBuilder.append(URLEncoder.encode(key, "UTF-8"));
                postQueryStringBuilder.append("=");
                postQueryStringBuilder.append(URLEncoder.encode(value, "UTF-8"));
                separator = "&";
            }

            for (final String key : arrayParams.keySet()) {
                final List<String> values = arrayParams.get(key);

                for (final String value : values) {
                    if (value == null) { continue; }
                    postQueryStringBuilder.append(separator);
                    postQueryStringBuilder.append(URLEncoder.encode(key + "[]", "UTF-8"));
                    postQueryStringBuilder.append("=");
                    postQueryStringBuilder.append(URLEncoder.encode(value, "UTF-8"));
                    separator = "&";
                }
            }
        }
        catch (final Exception exception) {
            Logger.log(exception);
        }
        return MutableByteArray.wrap(StringUtil.stringToBytes(postQueryStringBuilder.toString()));
    }

    protected Map<String, String> _getParams;
    protected Map<String, String> _postParams;
    protected Map<String, List<String>> _arrayGetParams;
    protected Map<String, List<String>> _arrayPostParams;

    public WebRequest() {
        _getParams = new HashMap<String, String>();
        _postParams = new HashMap<String, String>();

        _arrayGetParams = new HashMap<String, List<String>>();
        _arrayPostParams = new HashMap<String, List<String>>();
    }

    public void setGetParam(String key, String value) {
        if (key == null) { return; }

        if (value == null) {
            _getParams.remove(key);
        }
        else {
            _getParams.put(key, value);
        }
    }

    public void setPostParam(String key, String value) {
        if (key == null) { return; }

        if (value == null) {
            _postParams.remove(key);
        }
        else {
            _postParams.put(key, value);
        }
    }

    public void addGetParam(String key, String value) {
        if (! _arrayGetParams.containsKey(key)) {
            _arrayGetParams.put(key, new ArrayList<String>());
        }

        _getParams.remove(key);

        final List<String> array = _arrayGetParams.get(key);
        array.add(value);
    }

    public void addPostParam(String key, String value) {
        if (! _arrayPostParams.containsKey(key)) {
            _arrayPostParams.put(key, new ArrayList<String>());
        }

        final List<String> array = _arrayPostParams.get(key);
        array.add(value);
    }

    public Map<String, String> getGetParams() {
        final Map<String, String> getParams = new HashMap<String, String>();
        for (final String key : _getParams.keySet()) {
            final String value = _getParams.get(key);
            getParams.put(key, value);
        }
        return getParams;
    }

    public Map<String, String> getPostParams() {
        final Map<String, String> postParams = new HashMap<String, String>();
        for (final String key : _postParams.keySet()) {
            final String value = _postParams.get(key);
            postParams.put(key, value);
        }
        return postParams;
    }

    @Override
    public void execute(final boolean nonblocking, final Callback callback) {
        if (_queryString.isEmpty()) {
            _queryString = StringUtil.bytesToString(WebRequest.buildQueryString(_getParams, _arrayGetParams).unwrap());
        }

        if (_method == Request.HttpMethod.POST) {
            if (_postData.isEmpty()) {
                _postData = WebRequest.buildQueryString(_postParams, _arrayPostParams);
            }

            _setHeaders.put("Content-Type", "application/x-www-form-urlencoded");
            _setHeaders.put("Charset", "UTF-8");
            _setHeaders.put("Content-Length", Integer.toString(_postData.getByteCount()));
        }
        super.execute(nonblocking, callback);
    }
}
