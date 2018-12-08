package com.softwareverde.io;

import com.softwareverde.util.DateUtil;
import com.softwareverde.util.Util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Logger {
    public interface LogCallback {
        void onLog(Object message);
    }
    private static final Integer MAX_BATCH_COUNT = 128;
    private static final Object _messagePin = new Object();
    private static final ConcurrentLinkedQueue<String> _queuedMessages = new ConcurrentLinkedQueue<String>();

    private static final Thread _logThread = new Thread(new Runnable() {
        private String _dequeueMessages(final Integer maxMessageCount) {
            final StringBuilder stringBuilder = new StringBuilder();
            int messageCount = 0;
            String message;
            while ((message = _queuedMessages.poll()) != null) {
                stringBuilder.append(message);
                stringBuilder.append("\n");

                messageCount += 1;
                if (messageCount >= maxMessageCount) { break; }
            }

            return stringBuilder.toString();
        }

        @Override
        public void run() {
            boolean shouldContinue = true;
            while (shouldContinue) {
                synchronized (_messagePin) {
                    try {
                        _messagePin.wait();
                    }
                    catch (final InterruptedException exception) {
                        shouldContinue = false;
                    }
                }

                final String concatenatedMessages = _dequeueMessages(MAX_BATCH_COUNT);
                if (! concatenatedMessages.isEmpty()) {
                    System.out.print(concatenatedMessages);
                    System.out.flush();
                }
            }
        }
    });
    static {
        _logThread.setName("Log Thread - " + _logThread.getId());
        _logThread.setDaemon(true);
        _logThread.start();
    }

    public static LogCallback LOG_CALLBACK = null;
    public static LogCallback ERROR_CALLBACK = null;

    private static String _toString(final Object object) {
        try (final StringWriter stringWriter = new StringWriter()) {
            stringWriter.append("[");
            stringWriter.append(DateUtil.timestampToDatetimeString(System.currentTimeMillis(), TimeZone.getDefault()));
            stringWriter.append("] ");
            try (final PrintWriter printWriter = new PrintWriter(stringWriter)) {
                if (object instanceof Exception) {
                    final String metadata = ("[" + _getMetadata((Exception) object, 1) + "]");
                    stringWriter.append(metadata);
                    ((Exception) object).printStackTrace(printWriter);
                }
                else {
                    final String metadata = ("[" + _getMetadata(new Exception(), 2) + "] ");
                    stringWriter.append(metadata);
                    stringWriter.append(Util.coalesce(object, "null").toString());
                }

                return stringWriter.toString();
            }
        }
        catch (final Exception exception) { exception.printStackTrace(); }

        return "null";
    }

    public static String _getMetadata(final Exception exception, final Integer backtraceIndex) {
        final StackTraceElement stackTraceElements[] = exception.getStackTrace();
        final StackTraceElement stackTraceElement = stackTraceElements[Math.min(backtraceIndex, stackTraceElements.length - 1)];
        return stackTraceElement.getFileName() + ":" + stackTraceElement.getLineNumber();
    }

    public static void log(final Object object) {
        final String string = _toString(object);

        if (_logThread.isAlive()) {
            _queuedMessages.add(string);

            synchronized (_messagePin) {
                _messagePin.notifyAll();
            }
        }
        else {
            System.out.println(string);
        }

        final LogCallback logCallback = LOG_CALLBACK;
        if (logCallback != null) {
            logCallback.onLog(object);
        }
    }

    public static void error(final Object object) {
        final String string = _toString(object);
        System.err.println(string);

        final LogCallback errorCallback = ERROR_CALLBACK;
        if (errorCallback != null) {
            errorCallback.onLog(object);
        }
    }

    public static void shutdown() {
        _logThread.interrupt();
        try { _logThread.join(); } catch (final Exception exception) { }
    }
}
