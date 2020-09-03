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

    protected final SystemTime _systemTime = new SystemTime();
    protected final String _logDirectory;
    protected final String _logFilePrefix;

    protected final Long _maxByteCount;
    protected Long _currentByteCount = 0L;
    protected File _currentFile;
    protected OutputStream _currentOutputStream;

    protected File _createNewCurrentFile() throws IOException {

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
            final String filename = (_logDirectory + File.separator + _logFilePrefix + dateTimeString + postfix + ".log");
            final File file = new File(filename);
            final boolean newFileWasCreated = file.createNewFile();

            if (newFileWasCreated) {
                return file;
            }

            sequenceNumber = (Util.coalesce(sequenceNumber) + 1);
        }
    }

    protected void _compressLogFile(final File logFile) throws IOException {
        try (final FileInputStream fileInputStream = new FileInputStream(logFile)) {
            final File outputFile = new File(logFile.getPath() + ".gz");
            // if (outputFile.exists()) {
            //     throw new IOException("Attempted to overwrite logfile: " + outputFile);
            // }

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

    protected void _writeString(final String string) {
        try {
            final byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
            _currentOutputStream.write(bytes);

            _currentByteCount += bytes.length;
            if (_currentByteCount >= _maxByteCount) {
                _currentOutputStream.flush();
                _currentOutputStream.close();

                final File oldFile = _currentFile;

                _currentFile = _createNewCurrentFile();
                _currentOutputStream = new BufferedOutputStream(new FileOutputStream(_currentFile), PAGE_SIZE);

                _compressLogFile(oldFile);
                oldFile.delete();
            }
        }
        catch (final IOException exception) { }
    }

    public FileLogWriter(final String logDirectory, final String logFilePrefix) throws IOException {
        this(logDirectory, logFilePrefix, (64L * ByteUtil.Unit.Binary.MEBIBYTES));
    }

    public FileLogWriter(final String logDirectory, final String logFilePrefix, final Long maxByteCount) throws IOException {
        _logDirectory = logDirectory;
        _logFilePrefix = logFilePrefix;
        _maxByteCount = maxByteCount;

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

        _currentFile = _createNewCurrentFile();
        _currentOutputStream = new BufferedOutputStream(new FileOutputStream(_currentFile), PAGE_SIZE);
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

    public void close() {
        try {
            _currentOutputStream.flush();
            _currentOutputStream.close();
        }
        catch (final IOException exception) { }
    }
}