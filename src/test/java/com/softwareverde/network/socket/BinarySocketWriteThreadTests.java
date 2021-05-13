package com.softwareverde.network.socket;

import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.util.ByteBuffer;
import com.softwareverde.util.Container;
import com.softwareverde.util.StringUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.OutputStream;

public class BinarySocketWriteThreadTests extends UnitTest {
    @Before @Override
    public void before() throws Exception {
        super.before();
    }

    @After @Override
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_flush_and_disconnect() throws Exception {
        // Setup
        final ByteBuffer byteBuffer = new ByteBuffer();
        final Container<Boolean> flushWasCalled = new Container<>(false);
        final Container<Boolean> closeWasCalled = new Container<>(false);
        final OutputStream outputStream = new OutputStream() {
            @Override
            public void write(final int b) {
                byteBuffer.appendBytes(new byte[]{ (byte) (b & 0xFF) }, 1);
            }

            @Override
            public void flush() {
                flushWasCalled.value = true;
            }

            @Override
            public void close() {
                closeWasCalled.value = true;
            }
        };

        final BinarySocketWriteThread writeThread = new BinarySocketWriteThread(BinarySocket.DEFAULT_BUFFER_PAGE_BYTE_COUNT, BinarySocket.DEFAULT_MAX_BUFFER_BYTE_COUNT);
        writeThread.setOutputStream(outputStream);

        final ByteArray bytesToWrite = ByteArray.wrap(StringUtil.stringToBytes("0123456789ABCDEFFEDCBA9876543210"));

        writeThread.start();

        // Action
        writeThread.write(bytesToWrite);
        writeThread.close();

        // Assert
        writeThread.join();
        Assert.assertTrue(flushWasCalled.value);
        Assert.assertTrue(closeWasCalled.value);
        Assert.assertEquals(bytesToWrite, ByteArray.wrap(byteBuffer.readBytes(byteBuffer.getByteCount())));
    }
}
