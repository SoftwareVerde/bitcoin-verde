package com.softwareverde.cryptography.secp256k1;

import com.softwareverde.bitcoin.secp256k1.Secp256k1;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECFieldElement;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;

public class MultisetHash {
    // https://arxiv.org/pdf/1601.06502.pdf
    // https://github.com/tomasvdw/bips/blob/master/ecmh.mediawiki

    protected ECPoint _point;

    protected ECPoint _convertToPoint(final BigInteger x) {
        if (x.compareTo(Schnorr.CURVE_P) >= 0) { return null; } // Public key is not on the curve.

        final int byteCount = 32;
        final byte[] encodedCompressedPoint = new byte[byteCount + 1];
        {
            final byte[] xPointBytes = x.toByteArray();
            final boolean yCoordinateIsEven = (! ByteUtil.getBit(xPointBytes, 0));
            encodedCompressedPoint[0] = (yCoordinateIsEven ? PublicKey.COMPRESSED_FIRST_BYTE_0 : PublicKey.COMPRESSED_FIRST_BYTE_1);
            ByteUtil.setBytes(encodedCompressedPoint, ByteUtil.getTailBytes(xPointBytes, byteCount), 1);
        }

        final byte[] decompressedPublicKeyBytes = Secp256k1.decompressPoint(encodedCompressedPoint);
        if (decompressedPublicKeyBytes == null) { return null; }

        final ECCurve curve = Secp256k1.CURVE_DOMAIN.getCurve();
        return curve.decodePoint(decompressedPublicKeyBytes);
    }

    protected ECPoint _getPoint(final ByteArray byteArray) {
        final Sha256Hash byteArrayHash = HashUtil.sha256(byteArray);

        long n = 0L;
        while (true) {
            final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
            byteArrayBuilder.appendBytes(ByteArray.wrap(ByteUtil.longToBytes(n)), Endian.LITTLE);
            byteArrayBuilder.appendBytes(byteArrayHash, Endian.BIG);

            final Sha256Hash xBytes = HashUtil.sha256(byteArrayBuilder);

            final ECPoint ecPoint = _convertToPoint(new BigInteger(1, xBytes.getBytes()));
            if (ecPoint != null) {
                return ecPoint;
            }

            n += 1L;
        }
    }

    public MultisetHash() {
        _point = Secp256k1.CURVE.getInfinity();
    }

    public void merge(final MultisetHash multisetHash) {
        final ECPoint multisetPoint = multisetHash._point;
        _point = _point.add(multisetPoint).normalize();
    }

    public void addItem(final ByteArray byteArray) {
        final ECPoint point = _getPoint(byteArray);
        _point = _point.add(point).normalize();
    }

    public void removeItem(final ByteArray byteArray) {
        final ECPoint point = _getPoint(byteArray);
        _point = _point.subtract(point).normalize();
    }

    public Sha256Hash getHash() {
        if (_point.isInfinity()) {
            return Sha256Hash.EMPTY_HASH;
        }

        final ECFieldElement xCoordinate = _point.getXCoord();
        final ECFieldElement yCoordinate = _point.getYCoord();

        final BigInteger xBigInteger = xCoordinate.toBigInteger();
        final BigInteger yBigInteger = yCoordinate.toBigInteger();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(ByteUtil.getTailBytes(xBigInteger.toByteArray(), Sha256Hash.BYTE_COUNT));
        byteArrayBuilder.appendBytes(ByteUtil.getTailBytes(yBigInteger.toByteArray(), Sha256Hash.BYTE_COUNT));

        return HashUtil.sha256(byteArrayBuilder);
    }
}
