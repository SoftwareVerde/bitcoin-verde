package com.softwareverde.io;

public class Logger {
    public interface LogCallback {
        void onLog(Object message);
    }

    public static LogCallback LOG_CALLBACK = null;

    public static void log(final Object object) {
        if (object instanceof Exception) {
            ((Exception) object).printStackTrace();
        }
        else {
            System.out.println(object);
        }

        final LogCallback logCallback = LOG_CALLBACK;
        if (logCallback != null) {
            logCallback.onLog(object);
        }
    }
}
