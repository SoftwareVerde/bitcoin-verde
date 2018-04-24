package com.softwareverde.bitcoin.secp256k1.signature;


import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.constable.Const;
import com.softwareverde.constable.Constable;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.ImmutableByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

public class Signature implements Const, Constable<Signature> {
    protected static final Byte DER_MAGIC_NUMBER = 0x30;
    protected static final Byte DER_INTEGER_TYPE = 0x02;

    protected static MutableByteArray _readDerEncodedInteger(final ByteArrayReader byteArrayReader) {
        final Endian endianness = Endian.BIG;

        final byte type = byteArrayReader.readByte();
        if (type != DER_INTEGER_TYPE) { return null; }

        final Integer byteCount = byteArrayReader.readInteger(1, endianness);
        if (byteArrayReader.remainingByteCount() < byteCount) { return null; }

        final byte[] bytes = byteArrayReader.readBytes(byteCount, endianness);
        return MutableByteArray.wrap(bytes);
    }

    /**
     * Decodes bytes as a DER-encoded byte[].
     */
    public static Signature fromBytes(final ByteArray bytes) {
        final Endian endianness = Endian.BIG;

        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final byte magicNumber = byteArrayReader.readByte();
        if (magicNumber != DER_MAGIC_NUMBER) { return null; }

        final Integer sequenceByteCount = byteArrayReader.readInteger(1, endianness);
        if (byteArrayReader.remainingByteCount() < sequenceByteCount) { return null; }

        final MutableByteArray signatureR = _readDerEncodedInteger(byteArrayReader);
        final MutableByteArray signatureS = _readDerEncodedInteger(byteArrayReader);

        if (byteArrayReader.didOverflow()) { return null; }
        if (signatureR == null) { return null; }
        if (signatureS == null) { return null; }

        return new Signature(signatureR, signatureS);
    }

    protected final ByteArray _r;
    protected final ByteArray _s;

    private Signature(final MutableByteArray r, final MutableByteArray s) {
        _r = r;
        _s = s;
    }

    public Signature(final byte[] r, final byte[] s) {
        _r = new ImmutableByteArray(r);
        _s = new ImmutableByteArray(s);
    }

    public Signature(final ByteArray r, final ByteArray s) {
        _r = r.asConst();
        _s = s.asConst();
    }

    public ByteArray getR() {
        return _r;
    }

    public ByteArray getS() {
        return _s;
    }

    public ByteArray encodeAsDer() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendByte(DER_MAGIC_NUMBER);

        final int byteCount = (_r.getByteCount() + _s.getByteCount() + 2 + 2);
        byteArrayBuilder.appendByte((byte) byteCount);

        byteArrayBuilder.appendByte(DER_INTEGER_TYPE);
        byteArrayBuilder.appendByte((byte) _r.getByteCount());
        byteArrayBuilder.appendBytes(_r, Endian.BIG);

        byteArrayBuilder.appendByte(DER_INTEGER_TYPE);
        byteArrayBuilder.appendByte((byte) _s.getByteCount());
        byteArrayBuilder.appendBytes(_s, Endian.BIG);

        return MutableByteArray.wrap(byteArrayBuilder.build());
    }

    @Override
    public Signature asConst() {
        return this;
    }
}
