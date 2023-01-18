package com.softwareverde.util.bytearray;

import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.ByteBuffer;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.StringUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.SecureRandom;

public class ByteArrayStreamTests extends UnitTest {
    @Before @Override
    public void before() throws Exception {
        super.before();
    }

    @After @Override
    public void after() throws Exception {
        super.after();
    }

    protected File _makeFile(final String content) throws Exception {
        final File file = File.createTempFile("input", ".dat");
        file.deleteOnExit();

        try (final FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            fileOutputStream.write(StringUtil.stringToBytes(content));
            fileOutputStream.flush();
        }

        return file;
    }

    protected MutableList<File> _makeFiles(final String expectedValue, final Integer splitEveryRandomNthWord, final SecureRandom secureRandom) throws Exception {
        final MutableList<File> files = new MutableArrayList<>();

        String separator = "";
        StringBuilder stringBuilder = new StringBuilder();
        for (final String value : expectedValue.split(" ")) {
            stringBuilder.append(separator);
            stringBuilder.append(value);

            separator = " ";

            if ( (stringBuilder.length() > 0) && (secureRandom.nextInt() % splitEveryRandomNthWord == 0) ) {
                final String fileContents = stringBuilder.toString();
                final File file = _makeFile(fileContents);
                files.add(file);
                System.out.println("Wrote: " + fileContents.length() + " bytes to " + file.getPath());
                stringBuilder = new StringBuilder();
            }
        }
        if (stringBuilder.length() > 0) {
            final String fileContents = stringBuilder.toString();
            final File file = _makeFile(fileContents);
            files.add(file);
            System.out.println("Wrote: " + fileContents.length() + " bytes to " + file.getPath());
        }

        return files;
    }

    @Test
    public void should_read_consecutive_bytes_across_multiple_file_streams() throws Exception {
        // Setup
        final SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
        secureRandom.setSeed(HexUtil.hexStringToByteArray("7F"));

        final String expectedValue = "Mary had a little lamb. Her fleece was white as snow. Everywhere that Mary went her sheep was sure to go.";
        final MutableList<File> files = _makeFiles(expectedValue, 5, secureRandom);

        final StringBuilder stringBuilder = new StringBuilder();
        try (final ByteArrayStream byteArray = new ByteArrayStream()) {
            for (final File file : files) {
                final FileInputStream inputStream = new FileInputStream(file);
                byteArray.appendInputStream(inputStream);
            }

            // Action
            int remainingByteCount = expectedValue.length();
            while (remainingByteCount > 0) {
                final int nextReadByteCount = (remainingByteCount < 2 ? remainingByteCount : (remainingByteCount / 2));
                System.out.println("Reading " + nextReadByteCount + " bytes.");
                final byte[] bytes = byteArray.readBytes(nextReadByteCount);
                if (bytes.length != nextReadByteCount) {
                    throw new RuntimeException("Read " + bytes.length + " bytes, expected " + nextReadByteCount + ".");
                }

                final String string = StringUtil.bytesToString(bytes);
                System.out.println("Read: " + string + " " + HexUtil.toHexString(bytes));
                stringBuilder.append(string);
                remainingByteCount -= bytes.length;
            }

            Assert.assertFalse(byteArray.didOverflow());
        }

        // Assert
        Assert.assertEquals(expectedValue, stringBuilder.toString());
    }

    @Test
    public void should_return_number_of_bytes_requested_if_content_is_nonexistent() throws Exception {
        // Setup
        final SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
        secureRandom.setSeed(HexUtil.hexStringToByteArray("7F"));

        final String sourceValue = "0123 4567 89AB CDEF FEDC BA98 7654 3210";
        final ByteArray expectedValue;
        {
            final MutableByteArray bytes = new MutableByteArray(sourceValue.length() + 10);
            ByteUtil.setBytes(bytes, MutableByteArray.wrap(StringUtil.stringToBytes(sourceValue)), 0);
            expectedValue = bytes;
        }

        final MutableList<File> files = _makeFiles(sourceValue, 3, secureRandom);

        final ByteBuffer byteBuffer = new ByteBuffer();
        try (final ByteArrayStream byteArray = new ByteArrayStream()) {
            for (final File file : files) {
                final FileInputStream inputStream = new FileInputStream(file);
                byteArray.appendInputStream(inputStream);
            }

            // Action
            int remainingByteCount = (sourceValue.length() + 10);
            while (remainingByteCount > 0) {
                final int nextReadByteCount = (remainingByteCount < 2 ? remainingByteCount : (remainingByteCount / 2));
                System.out.println("Reading " + nextReadByteCount + " bytes.");
                final byte[] bytes = byteArray.readBytes(nextReadByteCount);
                if (bytes.length != nextReadByteCount) {
                    throw new RuntimeException("Read " + bytes.length + " bytes, expected " + nextReadByteCount + ".");
                }
                System.out.println("Read " + HexUtil.toHexString(bytes));
                byteBuffer.appendBytes(bytes, bytes.length);
                remainingByteCount -= bytes.length;
            }

            Assert.assertTrue(byteArray.didOverflow());
        }

        final ByteArray readValue = ByteArray.wrap(byteBuffer.readBytes(expectedValue.getByteCount()));

        // Assert
        Assert.assertEquals(expectedValue, readValue);
    }

    @Test
    public void should_peak_then_read_consecutive_bytes_across_multiple_file_streams() throws Exception {
        // Setup
        final SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
        secureRandom.setSeed(HexUtil.hexStringToByteArray("7F"));

        final String expectedValue = "Mary had a little lamb. Her fleece was white as snow. Everywhere that Mary went her sheep was sure to go.";
        final MutableList<File> files = _makeFiles(expectedValue, 5, secureRandom);

        final StringBuilder peakingStringBuilder = new StringBuilder();
        final StringBuilder stringBuilder = new StringBuilder();
        try (final ByteArrayStream byteArray = new ByteArrayStream()) {
            for (final File file : files) {
                final FileInputStream inputStream = new FileInputStream(file);
                byteArray.appendInputStream(inputStream);
            }

            // Action
            {
                System.out.println("Peaking " + expectedValue.length() + " bytes.");
                final byte[] peakedBytes = byteArray.peakBytes(expectedValue.length());
                final String string = StringUtil.bytesToString(peakedBytes);
                System.out.println("Peaked: " + string + " " + HexUtil.toHexString(peakedBytes));
                peakingStringBuilder.append(string);
            }

            int remainingByteCount = expectedValue.length();
            while (remainingByteCount > 0) {
                final int nextReadByteCount = (remainingByteCount < 2 ? remainingByteCount : (remainingByteCount / 2));
                System.out.println("Reading " + nextReadByteCount + " bytes.");
                final byte[] bytes = byteArray.readBytes(nextReadByteCount);
                if (bytes.length != nextReadByteCount) {
                    throw new RuntimeException("Read " + bytes.length + " bytes, expected " + nextReadByteCount + ".");
                }

                final String string = StringUtil.bytesToString(bytes);
                System.out.println("Read: " + string + " " + HexUtil.toHexString(bytes));
                stringBuilder.append(string);
                remainingByteCount -= bytes.length;
            }

            Assert.assertFalse(byteArray.didOverflow());
        }

        // Assert
        Assert.assertEquals(expectedValue, stringBuilder.toString());
        Assert.assertEquals(expectedValue, peakingStringBuilder.toString());
    }

    @Test
    public void should_peak_read_consecutive_bytes_across_multiple_file_stream_and_check_availability() throws Exception {
        // Setup
        final SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
        secureRandom.setSeed(HexUtil.hexStringToByteArray("7F"));

        final String expectedValue = "Mary had a little lamb. Her fleece was white as snow. Everywhere that Mary went her sheep was sure to go.";
        final String expectedPeakedValue = "Mary had a fleece that her sheep was.";
        final MutableList<File> files = _makeFiles(expectedValue, 5, secureRandom);

        final StringBuilder peakedStringBuilder = new StringBuilder();
        final StringBuilder stringBuilder = new StringBuilder();
        try (final ByteArrayStream byteArray = new ByteArrayStream()) {
            for (final File file : files) {
                final FileInputStream inputStream = new FileInputStream(file);
                byteArray.appendInputStream(inputStream);
            }

            // Action

            { // Peak "Mary had a "
                final byte[] peakedBytes = byteArray.peakBytes(11);
                peakedStringBuilder.append(StringUtil.bytesToString(peakedBytes));
                Assert.assertTrue(byteArray.hasBytes());
            }

            { // Read "Mary had a little lamb. Her "
                final byte[] readBytes = byteArray.readBytes(28);
                stringBuilder.append(StringUtil.bytesToString(readBytes));
                Assert.assertTrue(byteArray.hasBytes());
            }

            { // Peak "fleece "
                final byte[] peakedBytes = byteArray.peakBytes(7);
                peakedStringBuilder.append(StringUtil.bytesToString(peakedBytes));
                Assert.assertTrue(byteArray.hasBytes());
            }

            { // Read "fleece was white as snow. Everywhere "
                final byte[] readBytes = byteArray.readBytes(37);
                stringBuilder.append(StringUtil.bytesToString(readBytes));
                Assert.assertTrue(byteArray.hasBytes());
            }

            { // Peak "that "
                final byte[] peakedBytes = byteArray.peakBytes(5);
                peakedStringBuilder.append(StringUtil.bytesToString(peakedBytes));
                Assert.assertTrue(byteArray.hasBytes());
            }

            { // Read "that Mary went "
                final byte[] readBytes = byteArray.readBytes(15);
                stringBuilder.append(StringUtil.bytesToString(readBytes));
                Assert.assertTrue(byteArray.hasBytes());
            }

            { // Peak "her sheep was"
                final byte[] peakedBytes = byteArray.peakBytes(13);
                peakedStringBuilder.append(StringUtil.bytesToString(peakedBytes));
                Assert.assertTrue(byteArray.hasBytes());
            }

            { // Read "her sheep was sure to go"
                final byte[] readBytes = byteArray.readBytes(24);
                stringBuilder.append(StringUtil.bytesToString(readBytes));
                Assert.assertTrue(byteArray.hasBytes());
            }

            { // Peak "."
                final byte[] peakedBytes = byteArray.peakBytes(1);
                peakedStringBuilder.append(StringUtil.bytesToString(peakedBytes));
                Assert.assertTrue(byteArray.hasBytes());
            }

            { // Read "."
                final byte[] readBytes = byteArray.readBytes(1);
                stringBuilder.append(StringUtil.bytesToString(readBytes));
                Assert.assertFalse(byteArray.hasBytes());
            }

            Assert.assertFalse(byteArray.didOverflow());
        }

        // Assert
        Assert.assertEquals(expectedValue, stringBuilder.toString());
        Assert.assertEquals(expectedPeakedValue, peakedStringBuilder.toString());
    }
}
