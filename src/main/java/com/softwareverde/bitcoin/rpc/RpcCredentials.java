package com.softwareverde.bitcoin.rpc;

import com.softwareverde.util.Base64Util;
import com.softwareverde.util.StringUtil;

public class RpcCredentials {
    protected final String _authorizationHeaderKey = "authorization";

    protected final String _username;
    protected final String _password;

    public RpcCredentials(final String username, final String password) {
        _username = username;
        _password = password;
    }

    public String getUsername() {
        return _username;
    }

    public String getPassword() {
        return _password;
    }

    public String getAuthorizationHeaderKey() {
        return _authorizationHeaderKey;
    }

    public String getAuthorizationHeaderValue() {
        final String passcode = (_username + ":" + _password);
        final String encodedPasscode = Base64Util.toBase64String(StringUtil.stringToBytes(passcode));
        return ("Basic " + encodedPasscode);
    }
}
