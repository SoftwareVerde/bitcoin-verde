package com.softwareverde.test.logging;

import com.softwareverde.bitcoin.util.IoUtil;
import com.softwareverde.bitcoin.util.StringUtil;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.filelog.AnnotatedFileLog;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

// NOTE: This class is outside of the `com.softwareverde.logging` package so that the package name logic works as it does in production.

public class AnnotatedFileLogTests {
    public static File createTempDirectory() throws IOException {
        final File tempFile = File.createTempFile("temp", Long.toString(System.nanoTime()));

        if (! tempFile.delete()) {
            throw new IOException("Could not delete temp file: " + tempFile.getAbsolutePath());
        }

        if (! tempFile.mkdir()) {
            throw new IOException("Could not create temp directory: " + tempFile.getAbsolutePath());
        }

        return tempFile;
    }

    @Test
    public void should_write_error_message_to_file() throws Exception {
        final File tmpDirectory = AnnotatedFileLogTests.createTempDirectory();
        final AnnotatedFileLog log = AnnotatedFileLog.newInstance(tmpDirectory.getPath(), "", 1024L);

        try {
            final Class<?> callingClass = this.getClass();
            log.write(callingClass, LogLevel.INFO, "0123456789ABCDEFFEDCBA9876543210", null);
            log.flush();

            final String[] logFiles = tmpDirectory.list();
            Assert.assertNotNull(logFiles);
            Assert.assertEquals(1, logFiles.length);

            final File logFile = new File(tmpDirectory.getAbsoluteFile() + File.separator + logFiles[0]);
            Assert.assertTrue(logFile.exists());

            final String logContents = StringUtil.bytesToString(IoUtil.getFileContents(logFile));
            System.out.println(logContents);
            final boolean matchesContent = (logContents).matches("^\\[[0-9-:. ]+] \\[INFO] \\[[0-9A-z:.]+] 0123456789ABCDEFFEDCBA9876543210\\n");
            Assert.assertTrue(matchesContent);
        }
        finally {
            log.close();
            tmpDirectory.delete();
        }
    }

    @Test
    public void should_zip_log_after_exceeds_size() throws Exception {
        final File tmpDirectory = AnnotatedFileLogTests.createTempDirectory();
        final AnnotatedFileLog log = AnnotatedFileLog.newInstance(tmpDirectory.getPath(), "", 32L);

        try {
            final Class<?> callingClass = this.getClass();
            log.write(callingClass, LogLevel.INFO, "0123456789ABCDEFFEDCBA9876543210", null);
            log.flush();

            final String[] logFiles = tmpDirectory.list();
            Assert.assertNotNull(logFiles);
            Assert.assertEquals(2, logFiles.length);

            final File logFile = new File(tmpDirectory.getAbsoluteFile() + File.separator + (logFiles[0].endsWith("gz") ? logFiles[0] : logFiles[1]));
            Assert.assertTrue(logFile.exists());

            final byte[] zippedLogContents = IoUtil.getFileContents(logFile);

            final String unzippedStream;
            try (final InputStream inputStream = new ByteArrayInputStream(zippedLogContents)) {
                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                try (final GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
                    unzippedStream = StringUtil.bytesToString(IoUtil.readStream(gzipInputStream));
                }
            }

            System.out.println(unzippedStream);
            final boolean matchesContent = (unzippedStream).matches("^\\[[0-9-:. ]+] \\[INFO] \\[[0-9A-z:.]+] 0123456789ABCDEFFEDCBA9876543210\\n");
            Assert.assertTrue(matchesContent);
        }
        finally {
            log.close();
            tmpDirectory.delete();
        }
    }
}
