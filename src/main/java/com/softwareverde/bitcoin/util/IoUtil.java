package com.softwareverde.bitcoin.util;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.ByteBuffer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class IoUtil extends com.softwareverde.util.IoUtil {
    protected IoUtil() { }

    public static ByteArray getCompressedFileContents(final File file) {
        final int pageSize = (16 * 1024);

        try (
            final InputStream inputStream = new FileInputStream(file);
            final GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream, pageSize)
        ) {
            final ByteBuffer byteBuffer = new ByteBuffer();

            while (true) {
                final byte[] buffer = new byte[pageSize];
                final int byteCount = gzipInputStream.read(buffer, 0, pageSize);
                if (byteCount < 1) { break; }
                byteBuffer.appendBytes(buffer, byteCount);
            }

            return byteBuffer;
        }
        catch (final Exception exception) {
            Logger.trace("Unable to read file contents.", exception);
            return null;
        }
    }

    public static Boolean putCompressedFileContents(final File file, final ByteArray bytes) {
        final int pageSize = (16 * 1024);

        int bytesWritten = 0;
        int bytesRemaining = bytes.getByteCount();
        try (
            final OutputStream outputStream = new FileOutputStream(file);
            final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream, pageSize)
        ) {
            while (bytesRemaining > 0) {
                final byte[] buffer = bytes.getBytes(bytesWritten, Math.min(pageSize, bytesRemaining));
                gzipOutputStream.write(buffer);
                bytesWritten += buffer.length;
                bytesRemaining -= buffer.length;
            }
            outputStream.flush();
            return true;
        }
        catch (final Exception exception) {
            Logger.trace("Unable to write file contents.", exception);
            return false;
        }
    }
}
