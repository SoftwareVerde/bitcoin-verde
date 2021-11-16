package com.softwareverde.bitcoin.rpc.core;

import com.softwareverde.http.server.servlet.response.Response;

public class BitcoinCoreUtil {
    /**
     * Returns the appropriate verbatim error message that Bitcoin Core / BCHN responds with for an RPC response code.
     * Logic transpiled from: https://github.com/bitcoin-abc/bitcoin-abc/blob/master/src/bitcoin-cli.cpp
     */
    public static String getErrorMessage(final Integer responseCode) {
        if (responseCode == null) {
            return "send http request failed";
        }

        final int httpCode = responseCode;
        if (httpCode == 0) {
            return "couldn't connect to server";
        }
        else if (httpCode == Response.Codes.NOT_AUTHORIZED) {
            return "Authorization failed: Incorrect rpcuser or rpcpassword";
        }
        else if ( (httpCode >= 400) && (httpCode != Response.Codes.BAD_REQUEST) && (httpCode != Response.Codes.NOT_FOUND) && (httpCode != Response.Codes.SERVER_ERROR) ) {
            return ("server returned HTTP error " + responseCode);
        }
        else {
            return "no response from server";
        }
    }
}
