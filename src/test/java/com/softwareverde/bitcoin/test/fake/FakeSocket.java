package com.softwareverde.bitcoin.test.fake;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class FakeSocket extends Socket {
    public final ByteArrayInputStream inputStream;
    public final ByteArrayOutputStream outputStream;

    public FakeSocket() {
        this.inputStream = new ByteArrayInputStream(new byte[0]);
        this.outputStream = new ByteArrayOutputStream();
    }

    public FakeSocket(final byte[] inputBytes) {
        this.inputStream = new ByteArrayInputStream(inputBytes);
        this.outputStream = new ByteArrayOutputStream();
    }

    @Override
    public InputStream getInputStream() {
        return this.inputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        return this.outputStream;
    }
}