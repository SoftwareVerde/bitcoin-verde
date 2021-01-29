package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.bitcoin.rpc.RpcCredentials;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.util.Util;

public class StratumProperties {
    public static final Integer PORT = 3333;
    public static final Integer RPC_PORT = 3334;
    public static final Integer HTTP_PORT = 8082;
    public static final Integer TLS_PORT = 4482;

    protected Integer _port;
    protected Integer _rpcPort;
    protected String _bitcoinRpcUrl;
    protected Integer _bitcoinRpcPort;
    protected RpcCredentials _rpcCredentials;

    protected Integer _httpPort;
    protected String _rootDirectory;
    protected Integer _tlsPort;
    protected String _tlsKeyFile;
    protected String _tlsCertificateFile;
    protected String _cookiesDirectory;
    protected Boolean _useSecureCookies;

    public Integer getPort() { return _port; }
    public Integer getRpcPort() { return _rpcPort; }
    public String getBitcoinRpcUrl() { return _bitcoinRpcUrl; }
    public Integer getBitcoinRpcPort() {
        final Integer defaultRpcPort = BitcoinConstants.getDefaultRpcPort();
        return Util.coalesce(_bitcoinRpcPort, defaultRpcPort);
    }
    public RpcCredentials getRpcCredentials() {
        return _rpcCredentials;
    }

    public Integer getHttpPort() { return _httpPort; }
    public String getRootDirectory() { return _rootDirectory; }
    public Integer getTlsPort() { return _tlsPort; }
    public String getTlsKeyFile() { return _tlsKeyFile; }
    public String getTlsCertificateFile() { return _tlsCertificateFile; }
    public String getCookiesDirectory() { return _cookiesDirectory; }
    public Boolean areSecureCookiesEnabled() { return _useSecureCookies; }
}