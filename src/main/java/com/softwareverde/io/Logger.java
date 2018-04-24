package com.softwareverde.io;

public class Logger {
    public interface LogCallback {
        void onLog(Object message);
    }

    public static LogCallback LOG_CALLBACK = null;

    protected static String _getMetadata(final Exception exception, final Integer backtraceIndex) {
        final StackTraceElement stackTraceElements[] = exception.getStackTrace();
        final StackTraceElement stackTraceElement = stackTraceElements[backtraceIndex];
        return stackTraceElement.getFileName() + ":" + stackTraceElement.getLineNumber();
    }

    public static void log(final Object object) {
        if (object instanceof Exception) {
            System.out.println("[" + _getMetadata((Exception) object, 0) +"]");
            ((Exception) object).printStackTrace();
        }
        else {
            System.out.print("[" + _getMetadata(new Exception(), 1) + "] ");
            System.out.println(object);
        }

        final LogCallback logCallback = LOG_CALLBACK;
        if (logCallback != null) {
            logCallback.onLog(object);
        }
    }
}
