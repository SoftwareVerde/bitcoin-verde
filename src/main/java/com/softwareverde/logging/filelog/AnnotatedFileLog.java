package com.softwareverde.logging.filelog;

import com.softwareverde.logging.LineNumberAnnotatedLog;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.util.Package;
import com.softwareverde.util.type.time.SystemTime;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class AnnotatedFileLog extends LineNumberAnnotatedLog {
    public static AnnotatedFileLog newInstance(final String logDirectory) throws IOException {
        return AnnotatedFileLog.newInstance(logDirectory, "");
    }
    public static AnnotatedFileLog newInstance(final String logDirectory, final String logFilePrefix) throws IOException {
        final Writer writer = new FileLogWriter(logDirectory, logFilePrefix);
        return new AnnotatedFileLog(writer);
    }

    public static AnnotatedFileLog newInstance(final String logDirectory, final String logFilePrefix, final Long maxByteCount) throws IOException {
        final Writer writer = new FileLogWriter(logDirectory, logFilePrefix, maxByteCount);
        return new AnnotatedFileLog(writer);
    }

    protected final SystemTime _systemTime = new SystemTime();
    protected final TimeZone _timeZone = TimeZone.getDefault();

    protected AnnotatedFileLog(final Writer writer) {
        super(writer, writer);
    }

    @Override
    protected String _getTimestampAnnotation() {
        final Long timestamp = _systemTime.getCurrentTimeInMilliSeconds();
        final Date date = new Date(timestamp);

        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        dateFormat.setTimeZone(_timeZone);
        return dateFormat.format(date);
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

    @Override
    public void close() {
        ((FileLogWriter) _outWriter).close();
    }
}
