package com.softwareverde.bitcoin.server.configuration;

public class ProxyProperties {
    public static final Integer HTTP_PORT = 8080;
    public static final Integer TLS_PORT = 4480;
    
    protected Integer _httpPort;
    protected Integer _externalTlsPort;
    protected Integer _tlsPort;
    protected String[] _tlsKeyFiles;
    protected String[] _tlsCertificateFiles;

    public Integer getHttpPort() { return _httpPort; }
    public Integer getTlsPort() { return _tlsPort; }
    public Integer getExternalTlsPort() { return _externalTlsPort; }
    public String[] getTlsKeyFiles() { return _tlsKeyFiles; }
    public String[] getTlsCertificateFiles() { return _tlsCertificateFiles; }
}
