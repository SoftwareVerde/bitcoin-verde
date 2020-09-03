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

    protected static void finalizeLog(final File file, final OutputStream outputStream, final Boolean executeAsync) {
        final Runnable finalizeRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    outputStream.flush();
                    outputStream.close();

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

    protected void _createNewLogFile() throws IOException {

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
        File file;
        while (true) {
            final String postfix = (sequenceNumber == null ? "" : ("-" + sequenceNumber));
            final String filename = (_logDirectory + File.separator + _logFilePrefix + dateTimeString + postfix + ".log");
            file = new File(filename);
            final boolean newFileWasCreated = file.createNewFile();

            if (newFileWasCreated) {
                break;
            }

            sequenceNumber = (Util.coalesce(sequenceNumber) + 1);
        }

        _currentFile = file;
        _currentByteCount = 0L;
        _currentOutputStream = new BufferedOutputStream(new FileOutputStream(_currentFile), PAGE_SIZE);
    }

    protected void _writeString(final String string) {
        if (_currentOutputStream == null) { return; }

        try {
            final byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
            _currentOutputStream.write(bytes);

            _currentByteCount += bytes.length;
            if (_currentByteCount >= _maxByteCount) {
                final File oldFile = _currentFile;
                final OutputStream oldOutputStream = _currentOutputStream;

                _createNewLogFile();

                FileLogWriter.finalizeLog(oldFile, oldOutputStream, true);
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
        _logFilePrefix = Util.coalesce(logFilePrefix);
        _maxByteCount = Util.coalesce(maxByteCount, (64L * ByteUtil.Unit.Binary.MEBIBYTES));

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

        _createNewLogFile();
    }

    @Override
    public synchronized void write(final String string) {
        _writeString(string);
    }

    @Override
    public synchronized void write(final Throwable exception) {
        try (final StringWriter stringWriter = new StringWriter()) {
            stringWriter.write(exception.getMessage() + System.lineSeparator());
            try (final PrintWriter printWriter = new PrintWriter(stringWriter)) {
                exception.printStackTrace(printWriter);
                final String string = printWriter.toString();
                _writeString(string);
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
        FileLogWriter.finalizeLog(_currentFile, _currentOutputStream, false);

        _currentFile = null;
        _currentOutputStream = null;
        _currentByteCount = null;
    }
}