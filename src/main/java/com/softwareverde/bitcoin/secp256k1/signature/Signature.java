package com.softwareverde.bitcoin.secp256k1.signature;


import com.softwareverde.bitcoin.secp256k1.Secp256k1;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.Const;
import com.softwareverde.constable.Constable;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.ImmutableByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

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

    protected static byte[] _toDerEncodedInteger(final ByteArray byteArray) {
        final boolean firstByteHasSignBit = (ByteUtil.byteToInteger(byteArray.getByte(0)) > 0x7F);
        if (firstByteHasSignBit) {
            final byte[] bytes = new byte[1 + byteArray.getByteCount()];
            bytes[0] = 0x00;
            for (int i = 0; i < byteArray.getByteCount(); ++i) {
                bytes[i+1] = byteArray.getByte(i);
            }
            return bytes;
        }

        final Integer skippedByteCount;
        {
            int firstNonZeroIndex = 0;
            for (int i = 0; i < byteArray.getByteCount(); ++i) {
                firstNonZeroIndex = i;

                final byte b = byteArray.getByte(i);
                if (b != 0x00) {
                    final boolean byteHasSignBit = (ByteUtil.byteToInteger(byteArray.getByte(i)) > 0x7F);
                    // NOTE: 0x00 prefix bytes for r and s are not allowed except when their first byte would otherwise be above 0x7F...
                    if ((i > 0) && (byteHasSignBit)) {
                        // NOTE: i should always be greater than zero if byteHasSignBit is true due to the firstByteHasSignBit
                        //  check... however, the code here is defensive in case the skippedByteCount/firstByteHasSignBit check
                        //  ordering changes in the future.
                        firstNonZeroIndex -= 1;
                    }
                    break;
                }
            }
            skippedByteCount = firstNonZeroIndex;
        }
        final byte[] bytes = new byte[byteArray.getByteCount() - skippedByteCount];
        for (int i = skippedByteCount; i < byteArray.getByteCount(); ++i) {
            bytes[i - skippedByteCount] = byteArray.getByte(i);
        }
        return bytes;
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

    protected Boolean _isCanonical() {
        final BigInteger halfCurveOrder = curveN.shiftRight(1);
        final BigInteger s = new BigInteger(1, _s.getBytes());
        return (s.compareTo(halfCurveOrder) <= 0);
    }

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

        final byte[] rBytes = _toDerEncodedInteger(_r);
        final byte[] sBytes = _toDerEncodedInteger(_s);

        final int byteCount = (2 + rBytes.length + 2 + sBytes.length);
        byteArrayBuilder.appendByte((byte) byteCount);

        byteArrayBuilder.appendByte(DER_INTEGER_TYPE);
        byteArrayBuilder.appendByte((byte) rBytes.length);
        byteArrayBuilder.appendBytes(rBytes, Endian.BIG);

        byteArrayBuilder.appendByte(DER_INTEGER_TYPE);
        byteArrayBuilder.appendByte((byte) sBytes.length);
        byteArrayBuilder.appendBytes(sBytes, Endian.BIG);

        return MutableByteArray.wrap(byteArrayBuilder.build());
    }

    public Signature asCanonical() {
        if (_isCanonical()) { return this; }

        final BigInteger s = new BigInteger(1, _s.getBytes());
        final BigInteger newS = curveN.subtract(s);
        return new Signature(_r.getBytes(), newS.toByteArray());
    }

    public Boolean isCanonical() {
        return _isCanonical();
    }

    @Override
    public Signature asConst() {
        return this;
    }
}
