package com.softwareverde.bitcoin.server.module.electrum;

import com.softwareverde.bitcoin.server.configuration.ElectrumProperties;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;
import org.junit.After;
import org.junit.Before;

public class ElectrumModuleTests extends UnitTest {
    protected ElectrumProperties _electrumProperties;

    public ElectrumModuleTests() {
        _electrumProperties = new ElectrumProperties() {{
            _bitcoinRpcPort = 8334;
            _bitcoinRpcUrl = "localhost";
            _httpPort = 50001;
            _tlsPort = 50002;
        }};
    }

    @Before
    public void before() throws Exception {
        super.before();

        Logger.setLogLevel("com.softwareverde.network.socket", LogLevel.INFO);
    }

    @After
    public void after() throws Exception {
        super.after();
    }
}
