package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.logging.LogLevel;

import java.io.File;

public class ElectrumProperties {
    public static final Integer HTTP_PORT = 50001;
    public static final Integer TLS_PORT = 50002;

    protected String _bitcoinRpcUrl;
    protected Integer _bitcoinRpcPort;
    protected LogLevel _logLevel;
    protected File _dataDirectory;

    protected Integer _httpPort;
    protected Integer _tlsPort;
    protected String _tlsKeyFile;
    protected String _tlsCertificateFile;

    protected Address _donationAddress;

    public String getBitcoinRpcUrl() {
        return _bitcoinRpcUrl;
    }

    public Integer getBitcoinRpcPort() {
        return _bitcoinRpcPort;
    }

    public Integer getHttpPort() {
        return _httpPort;
    }

    public Integer getTlsPort() {
        return _tlsPort;
    }

    public String getTlsKeyFile() {
        return _tlsKeyFile;
    }

    public String getTlsCertificateFile() {
        return _tlsCertificateFile;
    }

    public Address getDonationAddress() {
        return _donationAddress;
    }

    public LogLevel getLogLevel() {
        return _logLevel;
    }

    public File getDataDirectory() {
        return _dataDirectory;
    }
}
