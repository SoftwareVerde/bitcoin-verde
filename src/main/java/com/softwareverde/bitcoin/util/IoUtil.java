package com.softwareverde.bitcoin.util;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.logging.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class IoUtil extends com.softwareverde.util.IoUtil {
    protected IoUtil() { }

    /**
     * Skips byteCount bytes within inputStream.
     *  The number of bytes skipped is returned.  If no bytes were skipped, then 0 is returned.
     *  If byteCount is less than 1, 0 is returned.
     *  This method is similar to InputStream::skip except that this function will not return until EOF is reached or byteCount bytes has been skipped.
     */
    public static Long skipBytes(final Long byteCount, final InputStream inputStream) {
        if (byteCount < 1) { return 0L; }

        int numberOfTimesSkipReturnedZero = 0;
        long skippedByteCount = 0L;
        while (skippedByteCount < byteCount) {
            final long skipReturnValue;
            try {
                skipReturnValue = inputStream.skip(byteCount - skippedByteCount);
            }
            catch (final IOException exception) { break; }

            skippedByteCount += skipReturnValue;

            if (skipReturnValue == 0) {
                numberOfTimesSkipReturnedZero += 1;
            }
            else {
                numberOfTimesSkipReturnedZero = 0;
            }

            // InputStream::skip can sometimes return zero for "valid" reasons, but does not report EOF...
            //  If skip returns zero 32 times (arbitrarily chosen), EOF is assumed...
            if (numberOfTimesSkipReturnedZero > 32) { break; }
        }
        return skippedByteCount;
    }

    public static Boolean fileExists(final String path) {
        final File file = new File(path);
        return file.exists();
    }

    public static Boolean isEmpty(final String path) {
        final File file = new File(path);
        if (! file.exists()) { return true; }
        if (! file.isFile()) { return true; }

        return (file.length() < 1);
    }

    public static Boolean putFileContents(final String filename, final ByteArray bytes) {
        final File file = new File(filename);
        return IoUtil.putFileContents(file, bytes);
    }

    public static Boolean putFileContents(final File file, final ByteArray bytes) {
        final int pageSize = (16 * 1024);

        int bytesWritten = 0;
        int bytesRemaining = bytes.getByteCount();
        try (final OutputStream outputStream = new FileOutputStream(file)) {
            while (bytesRemaining > 0) {
                final byte[] buffer = bytes.getBytes(bytesWritten, Math.min(pageSize, bytesRemaining));
                outputStream.write(buffer);
                bytesWritten += buffer.length;
                bytesRemaining -= buffer.length;
            }
            outputStream.flush();
            return true;
        }
        catch (final Exception exception) {
            Logger.warn("Unable to write file contents.", exception);
            return false;
        }
    }
}
