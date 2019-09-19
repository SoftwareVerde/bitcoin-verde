package com.softwareverde.bitcoin.test;

import com.softwareverde.logging.LineNumberAnnotatedLog;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;

public class UnitTest {
    static {
        Logger.setLog(LineNumberAnnotatedLog.getInstance());
        Logger.DEFAULT_LOG_LEVEL = LogLevel.ON;
    }
}
