package com.softwareverde.bitcoin.server.configuration;

public class ExplorerProperties {
    public static final Integer HTTP_PORT = 8081;
    public static final Integer TLS_PORT = 4481;

    protected Integer _port;
    protected String _rootDirectory;

    protected String _bitcoinRpcUrl;
    protected Integer _bitcoinRpcPort;

    protected String _stratumRpcUrl;
    protected Integer _stratumRpcPort;

    protected Integer _tlsPort;
    protected String _tlsKeyFile;
    protected String _tlsCertificateFile;

    public Integer getPort() { return _port; }
    public String getRootDirectory() { return _rootDirectory; }

    public String getBitcoinRpcUrl() { return _bitcoinRpcUrl; }
    public Integer getBitcoinRpcPort() { return _bitcoinRpcPort; }

    public String getStratumRpcUrl() { return _stratumRpcUrl; }
    public Integer getStratumRpcPort() { return _stratumRpcPort; }

    public Integer getTlsPort() { return _tlsPort; }
    public String getTlsKeyFile() { return _tlsKeyFile; }
    public String getTlsCertificateFile() { return _tlsCertificateFile; }
}