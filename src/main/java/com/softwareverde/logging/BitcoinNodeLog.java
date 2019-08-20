package com.softwareverde.logging;

import com.softwareverde.logging.log.BufferedSystemWriter;
import com.softwareverde.logging.log.SystemLog;
import com.softwareverde.util.Package;

public class BitcoinNodeLog extends LineNumberAnnotatedLog {
    protected static final Object INSTANCE_MUTEX = new Object();
    protected static volatile BitcoinNodeLog INSTANCE = null;
    public static BitcoinNodeLog getInstance() {
        if (INSTANCE == null) {
            synchronized (INSTANCE_MUTEX) {
                if (INSTANCE == null) {
                    INSTANCE = new BitcoinNodeLog(
                        SystemLog.wrapSystemStream(System.out),
                        SystemLog.wrapSystemStream(System.err)
                    );
                }
            }
        }

        return INSTANCE;
    }

    protected static volatile BitcoinNodeLog BUFFERED_INSTANCE = null;
    public static BitcoinNodeLog getBufferedInstance() {
        if (BUFFERED_INSTANCE == null) {
            synchronized (INSTANCE_MUTEX) {
                if (BUFFERED_INSTANCE == null) {
                    BUFFERED_INSTANCE = new BitcoinNodeLog(
                        new BufferedSystemWriter(BufferedSystemWriter.Type.SYSTEM_OUT),
                        new BufferedSystemWriter(BufferedSystemWriter.Type.SYSTEM_ERR)
                    );
                }
            }
        }

        return BUFFERED_INSTANCE;
    }

    protected BitcoinNodeLog(final Writer outWriter, final Writer errWriter) {
        super(outWriter, errWriter);
    }

    @Override
    protected String _getClassAnnotation(final Class<?> callingClass) {
        final Package pkg = Package.fromString(Package.getClassName(callingClass));
        return pkg.getParent() + "." + super._getClassAnnotation(callingClass);
    }

    @Override
    protected String _getLogLevelAnnotation(final LogLevel logLevel) {
        return logLevel.name();
    }
}
