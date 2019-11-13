package com.softwareverde.bitcoin.server.configuration;

public class WalletProperties {
    public static final Integer PORT = 8888;
    public static final Integer TLS_PORT = 4444;

    protected Integer _port;
    protected String _rootDirectory;

    protected Integer _tlsPort;
    protected String _tlsKeyFile;
    protected String _tlsCertificateFile;

    public Integer getPort() { return _port; }
    public String getRootDirectory() { return _rootDirectory; }

    public Integer getTlsPort() { return _tlsPort; }
    public String getTlsKeyFile() { return _tlsKeyFile; }
    public String getTlsCertificateFile() { return _tlsCertificateFile; }
}