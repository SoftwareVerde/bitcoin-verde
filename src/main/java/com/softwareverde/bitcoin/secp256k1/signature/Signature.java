package com.softwareverde.bitcoin.secp256k1.signature;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class Signature {
    protected static final Byte DER_MAGIC_NUMBER = 0x30;
    protected static final Byte DER_INTEGER_TYPE = 0x02;

    protected static byte[] _readDerEncodedInteger(final ByteArrayReader byteArrayReader) {
        final Endian endianness = Endian.BIG;

        final byte type = byteArrayReader.readByte();
        if (type != DER_INTEGER_TYPE) { return null; }

        final Integer byteCount = byteArrayReader.readInteger(1, endianness);
        if (byteArrayReader.remainingByteCount() < byteCount) { return null; }

        final byte[] bytes = byteArrayReader.readBytes(byteCount, endianness);
        return bytes;
    }

    /**
     * Decodes bytes as a DER-encoded byte[].
     */
    public static Signature fromBytes(final byte[] bytes) {
        final Endian endianness = Endian.BIG;

        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final byte magicNumber = byteArrayReader.readByte();
        if (magicNumber != DER_MAGIC_NUMBER) { return null; }

        final Integer sequenceByteCount = byteArrayReader.readInteger(1, endianness);
        if (byteArrayReader.remainingByteCount() < sequenceByteCount) { return null; }

        final byte[] signatureR = _readDerEncodedInteger(byteArrayReader);
        final byte[] signatureS = _readDerEncodedInteger(byteArrayReader);

        if (byteArrayReader.didOverflow()) { return null; }

        return new Signature(signatureR, signatureS);
    }

    protected final byte[] _r;
    protected final byte[]  _s;

    public Signature(final byte[] r, final byte[]  s) {
        _r = ByteUtil.copyBytes(r);
        _s = ByteUtil.copyBytes(s);
    }

    public byte[] getR() {
        return ByteUtil.copyBytes(_r);
    }

    public byte[] getS() {
        return ByteUtil.copyBytes(_s);
    }

    public byte[] encodeAsDer() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendByte(DER_MAGIC_NUMBER);

        final int byteCount = (_r.length + _s.length + 2 + 2);
        byteArrayBuilder.appendByte((byte) byteCount);

        byteArrayBuilder.appendByte(DER_INTEGER_TYPE);
        byteArrayBuilder.appendByte((byte) _r.length);
        byteArrayBuilder.appendBytes(_r, Endian.BIG);

        byteArrayBuilder.appendByte(DER_INTEGER_TYPE);
        byteArrayBuilder.appendByte((byte) _s.length);
        byteArrayBuilder.appendBytes(_s, Endian.BIG);

        return byteArrayBuilder.build();
    }
}
