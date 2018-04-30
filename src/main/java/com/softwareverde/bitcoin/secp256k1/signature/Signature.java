package com.softwareverde.bitcoin.secp256k1.signature;


import com.softwareverde.bitcoin.secp256k1.Secp256k1;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.constable.Const;
import com.softwareverde.constable.Constable;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.ImmutableByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERSequenceGenerator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;

public class Signature implements Const, Constable<Signature> {
    protected static final Byte DER_MAGIC_NUMBER = 0x30;
    protected static final Byte DER_INTEGER_TYPE = 0x02;

    protected static final BigInteger curveN = Secp256k1.CURVE_DOMAIN.getN();

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
        final byte[] rPrefixBytes;
        {
            final boolean firstByteHasSignBit = (ByteUtil.byteToInteger(_r.getByte(0)) > 0x7F);
            rPrefixBytes = new byte[(firstByteHasSignBit ? 1 : 0)];
        }

        final byte[] sPrefixBytes;
        {
            final boolean firstByteHasSignBit = (ByteUtil.byteToInteger(_s.getByte(0)) > 0x7F);
            sPrefixBytes = new byte[(firstByteHasSignBit ? 1 : 0)];
        }

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendByte(DER_MAGIC_NUMBER);

        final int byteCount = (2 + rPrefixBytes.length + _r.getByteCount() + 2 + sPrefixBytes.length +  _s.getByteCount());
        byteArrayBuilder.appendByte((byte) byteCount);

        final int rByteCount = (rPrefixBytes.length + _r.getByteCount());
        final int sByteCount = (sPrefixBytes.length + _s.getByteCount());

        byteArrayBuilder.appendByte(DER_INTEGER_TYPE);
        byteArrayBuilder.appendByte((byte) rByteCount);
        byteArrayBuilder.appendBytes(rPrefixBytes, Endian.BIG);
        byteArrayBuilder.appendBytes(_r, Endian.BIG);

        byteArrayBuilder.appendByte(DER_INTEGER_TYPE);
        byteArrayBuilder.appendByte((byte) sByteCount);
        byteArrayBuilder.appendBytes(sPrefixBytes, Endian.BIG);
        byteArrayBuilder.appendBytes(_s, Endian.BIG);

        return MutableByteArray.wrap(byteArrayBuilder.build());
    }

    public Signature toCanonical() {
        final BigInteger halfCurveOrder = curveN.shiftRight(1);
        final BigInteger s = new BigInteger(1, _s.getBytes());
        if (s.compareTo(halfCurveOrder) <= 0) { return this; }

        final BigInteger newS = curveN.subtract(s);
        return new Signature(_r.getBytes(), newS.toByteArray());
    }

    @Override
    public Signature asConst() {
        return this;
    }
}
