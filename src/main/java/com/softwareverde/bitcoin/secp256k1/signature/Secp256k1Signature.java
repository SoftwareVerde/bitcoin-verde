package com.softwareverde.bitcoin.secp256k1.signature;

import com.softwareverde.bitcoin.secp256k1.Secp256k1;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.ImmutableByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

import java.math.BigInteger;

public class Secp256k1Signature extends SignatureCore {
    protected static final Byte DER_MAGIC_NUMBER = 0x30;
    protected static final Byte DER_INTEGER_TYPE = 0x02;

    protected static final BigInteger curveN = Secp256k1.CURVE_DOMAIN.getN();

    protected static MutableByteArray _readDerEncodedInteger(final ByteArrayReader byteArrayReader) {
        final byte type = byteArrayReader.readByte();
        if (type != DER_INTEGER_TYPE) { return null; }

        final Integer byteCount = byteArrayReader.readInteger(1, Endian.BIG);
        if (byteArrayReader.remainingByteCount() < byteCount) { return null; }

        final byte[] bytes = byteArrayReader.readBytes(byteCount, Endian.BIG);
        return MutableByteArray.wrap(bytes);
    }

    protected static byte[] _toDerEncodedInteger(final ByteArray byteArray) {
        if (byteArray.isEmpty()) { return new byte[0]; }

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
     * Decodes bytes as a DER-encoded ByteArrayReader.
     */
    public static Secp256k1Signature fromBytes(final ByteArrayReader byteArrayReader) {
        final byte magicNumber = byteArrayReader.readByte();
        if (magicNumber != DER_MAGIC_NUMBER) { return null; }

        final Integer sequenceByteCount = byteArrayReader.readInteger(1, Endian.BIG);
        if (byteArrayReader.remainingByteCount() < sequenceByteCount) { return null; }

        final MutableByteArray signatureR = _readDerEncodedInteger(byteArrayReader);
        final MutableByteArray signatureS = _readDerEncodedInteger(byteArrayReader);

        if (byteArrayReader.didOverflow()) { return null; }
        if (signatureR == null) { return null; }
        if (signatureS == null) { return null; }

        return new Secp256k1Signature(signatureR, signatureS);
    }

    /**
     * Decodes bytes as a DER-encoded ByteArray.
     */
    public static Secp256k1Signature fromBytes(final ByteArray bytes) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        return Secp256k1Signature.fromBytes(byteArrayReader);
    }

    protected final ByteArray _r;
    protected final ByteArray _s;

    protected static Boolean isMinimallyEncoded(final ByteArray byteArray) {
        if (byteArray.getByteCount() < 2) { return true; }
        if ( (byteArray.getByte(0) == 0x00) && ((byteArray.getByte(1) & (byte) 0x80) == 0) ) { return false; }

        return true;
    }

    protected static Boolean isLowS(final BigInteger s) {
        final BigInteger halfCurveOrder = curveN.shiftRight(1);
        return (s.compareTo(halfCurveOrder) <= 0);
    }

    protected Boolean _isCanonical() {
        if (_r.getByteCount() == 0) { return false; }
        final BigInteger r = new BigInteger(1, _r.getBytes());
        if (r.compareTo(BigInteger.ZERO) < 0) { return false; } // R must be positive...
        if (! Secp256k1Signature.isMinimallyEncoded(_r)) { return false; }

        if (_s.getByteCount() == 0) { return false; }
        final BigInteger s = new BigInteger(1, _s.getBytes());
        if (s.compareTo(BigInteger.ZERO) < 0) { return false; } // S must be positive...
        if (! Secp256k1Signature.isMinimallyEncoded(_s)) { return false; }

        return Secp256k1Signature.isLowS(s);
    }

    private Secp256k1Signature(final MutableByteArray r, final MutableByteArray s) {
        _r = r;
        _s = s;
    }

    public Secp256k1Signature(final byte[] r, final byte[] s) {
        _r = new ImmutableByteArray(r);
        _s = new ImmutableByteArray(s);
    }

    public Secp256k1Signature(final ByteArray r, final ByteArray s) {
        _r = r.asConst();
        _s = s.asConst();
    }

    @Override
    public Type getType() {
        return Type.SECP256K1;
    }

    @Override
    public ByteArray getR() {
        return _r;
    }

    @Override
    public ByteArray getS() {
        return _s;
    }

    @Override
    public ByteArray encode() {
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

    @Override
    public Secp256k1Signature asCanonical() {
        if (_isCanonical()) { return this; }

        final BigInteger r = new BigInteger(1, _r.getBytes());
        final BigInteger s = new BigInteger(1, _s.getBytes());
        final BigInteger newS;
         if (! Secp256k1Signature.isLowS(s)) {
            newS = curveN.subtract(s);
        }
        else {
            newS = s;
        }
        return new Secp256k1Signature(r.toByteArray(), newS.toByteArray());
    }

    @Override
    public Boolean isEmpty() {
        return false;
    }

    @Override
    public Boolean isCanonical() {
        return _isCanonical();
    }
}
