package com.softwareverde.logging.filelog;

import com.softwareverde.logging.log.AbstractLog;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.GZIPOutputStream;

public class FileLogWriter implements AbstractLog.Writer {
    public static final Long DEFAULT_LOG_BYTE_COUNT = (64L * ByteUtil.Unit.Binary.MEBIBYTES);

    protected static final String NULL = "null";
    protected static final Integer PAGE_SIZE = (int) (16L * ByteUtil.Unit.Binary.KIBIBYTES);

    protected static void compressLogFile(final File logFile) throws IOException {
        try (final FileInputStream fileInputStream = new FileInputStream(logFile)) {
            final File outputFile = new File(logFile.getPath() + ".gz");

            // NOTE: GZIPOutputStream::close closes the underlying stream.
            try (final GZIPOutputStream gzippedOutputStream = new GZIPOutputStream(new FileOutputStream(outputFile))) {
                final byte[] buffer = new byte[PAGE_SIZE];
                int length;
                while ((length = fileInputStream.read(buffer)) != -1) {
                    gzippedOutputStream.write(buffer, 0, length);
                }
                gzippedOutputStream.flush();
            }
        }
    }

    protected static void finalizeLog(final File file, final Boolean executeAsync) {
        final Runnable finalizeRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    FileLogWriter.compressLogFile(file);

                    file.delete();
                }
                catch (final Exception exception) { }
            }
        };

        if (executeAsync) {
            (new Thread(finalizeRunnable)).start();
        }
        else {
            finalizeRunnable.run();
        }
    }

    protected final SystemTime _systemTime = new SystemTime();
    protected final String _logDirectory;
    protected final String _logFilePrefix;

    protected final Long _maxByteCount;
    protected Long _currentByteCount = 0L;
    protected File _currentFile;
    protected OutputStream _currentOutputStream;

    protected File _generateNewFile(final String extension) throws IOException {
        final String dateTimeString;
        {
            final Long now = _systemTime.getCurrentTimeInMilliSeconds();

            final TimeZone timeZone = TimeZone.getDefault();
            final Date date = new Date(now);
            final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
            dateFormat.setTimeZone(timeZone);
            dateTimeString = dateFormat.format(date);
        }

        Integer sequenceNumber = null;
        while (true) {
            final String postfix = (sequenceNumber == null ? "" : ("-" + sequenceNumber));
            final String filename = (_logDirectory + File.separator + (_logFilePrefix != null ? _logFilePrefix + "-" : "") + dateTimeString + postfix + extension);
            final File file = new File(filename);
            final boolean newFileWasCreated = file.createNewFile();

            if (newFileWasCreated) {
                return file;
            }

            sequenceNumber = (Util.coalesce(sequenceNumber) + 1);
        }
    }

    protected File _rotateLog(final Boolean createNewLog) throws IOException {
        final File oldFile;
        if (_currentFile != null) {
            oldFile = _generateNewFile(".log");
            final boolean moveWasSuccessful = _currentFile.renameTo(oldFile);
            if (! moveWasSuccessful) {
                throw new IOException("Unable to rotate log to: " + oldFile);
            }
        }
        else {
            oldFile = null;
        }

        if (createNewLog) {
            _currentFile = new File(_logDirectory + File.separator + (Util.isBlank(_logFilePrefix) ? "log" : _logFilePrefix) + ".log");
            _currentByteCount = 0L;
            _currentOutputStream = new BufferedOutputStream(new FileOutputStream(_currentFile), PAGE_SIZE);
        }
        else {
            _currentFile = null;
            _currentByteCount = 0L;
            _currentOutputStream = null;
        }

        return oldFile;
    }

    protected void _writeString(final String string) {
        if (_currentOutputStream == null) { return; }

        try {
            final byte[] bytes = Util.coalesce(string, NULL).getBytes(StandardCharsets.UTF_8);
            _currentOutputStream.write(bytes);

            _currentByteCount += bytes.length;
        }
        catch (final IOException exception) { }
    }

    protected void _conditionallyRotateLog() {
        try {
            if (_currentByteCount >= _maxByteCount) {
                _currentOutputStream.flush();
                _currentOutputStream.close();

                final File oldFile = _rotateLog(true);
                FileLogWriter.finalizeLog(oldFile, true);
            }
        }
        catch (final IOException exception) { }
    }

    public FileLogWriter(final String logDirectory, final String logFilePrefix) throws IOException {
        this(logDirectory, logFilePrefix, null);
    }

    public FileLogWriter(final String logDirectory, final String logFilePrefix, final Long maxByteCount) throws IOException {
        if (logDirectory == null) { throw new IOException("Unable to create log directory: " + null); }

        _logDirectory = logDirectory;
        _logFilePrefix = logFilePrefix;
        _maxByteCount = Util.coalesce(maxByteCount, DEFAULT_LOG_BYTE_COUNT);

        { // Ensure log directory exists or can can be written to...
            final File logDirectoryFile = new File(logDirectory);
            if (! logDirectoryFile.isDirectory()) {
                final boolean directoryWasCreated = logDirectoryFile.mkdirs();
                if (! directoryWasCreated) {
                    throw new IOException("Unable to create log directory: " + logDirectory);
                }
            }
            if (! logDirectoryFile.canWrite()) {
                throw new IOException("Unable to access log directory: " + logDirectory);
            }
        }

        _rotateLog(true);
    }

    @Override
    public synchronized void write(final String string) {
        _writeString(string);
        _conditionallyRotateLog();
    }

    @Override
    public synchronized void write(final Throwable exception) {
        if (exception == null) {
            _writeString(null);
            _conditionallyRotateLog();
            return;
        }

        try (final StringWriter stringWriter = new StringWriter()) {
            try (final PrintWriter printWriter = new PrintWriter(stringWriter)) {
                exception.printStackTrace(printWriter);
                final String string = stringWriter.toString();
                _writeString(string);
                _conditionallyRotateLog();
            }
        }
        catch (final IOException ioException) { }
    }

    @Override
    public synchronized void flush() {
        try {
            _currentOutputStream.flush();
        }
        catch (final IOException exception) { }
    }

    public synchronized void close() {
        if (_currentOutputStream != null) {
            try {
                _currentOutputStream.flush();
                _currentOutputStream.close();
            }
            catch (final IOException exception) { }
        }

        if (_currentFile != null) {
            try {
                final File oldLog = _rotateLog(false); // Unsets File and OutputStream members...
                FileLogWriter.finalizeLog(oldLog, false);
            }
            catch (final IOException exception) { }
        }
    }
}