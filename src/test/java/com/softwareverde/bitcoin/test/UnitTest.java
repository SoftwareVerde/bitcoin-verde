package com.softwareverde.bitcoin.test;

import com.softwareverde.logging.LineNumberAnnotatedLog;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;

public class UnitTest {
    static {
        Logger.setLog(LineNumberAnnotatedLog.getInstance());
        Logger.setLogLevel(LogLevel.ON);
        Logger.setLogLevel("com.softwareverde.concurrent.lock.IndexLock", LogLevel.ERROR);
    }

    public void before() throws Exception { }

    public void after() throws Exception { }
}
