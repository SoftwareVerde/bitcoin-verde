package com.softwareverde.bitcoin.secp256k1;

import com.softwareverde.bitcoin.secp256k1.key.PublicKey;
import com.softwareverde.bitcoin.secp256k1.signature.Signature;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;

public class Schnorr {
    public static final BigInteger TWO;
    public static final BigInteger CURVE_P;
    public static final BigInteger CURVE_N;

    protected static final ECCurve CURVE;
    protected static final ECPoint CURVE_POINT_G;
    public static final ECDomainParameters CURVE_DOMAIN;

    static {
        final ECNamedCurveParameterSpec curveParameterSpec = ECNamedCurveTable.getParameterSpec("secp256k1");
        CURVE_POINT_G = curveParameterSpec.getG();
        CURVE = curveParameterSpec.getCurve();
        CURVE_DOMAIN =  new ECDomainParameters(CURVE, CURVE_POINT_G, curveParameterSpec.getN());

        TWO = BigInteger.valueOf(2L);
        CURVE_P = new BigInteger(1, HexUtil.hexStringToByteArray("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F"));
        CURVE_N = new BigInteger(1, HexUtil.hexStringToByteArray("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141"));
    }

    protected static BigInteger _jacobi(final BigInteger x) {
        return x.modPow((CURVE_P.subtract(BigInteger.ONE).divide(TWO)), CURVE_P);
    }

    // https://github.com/sipa/bips/blob/bip-schnorr/bip-schnorr.mediawiki
    // https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/2019-05-15-schnorr.md
    public static Boolean verifySignature(final Signature signature, final PublicKey publicKey, final byte[] message) {

        // Schnorr Signatures, for message ("m") and public key ("P"), involve a point ("R"),
        //      integers ("e") and ("s") (picked by the signer), and generator G.
        //
        // "H" is the Sha256 hashing function, and "||" indicates byte concatenation.
        //
        // "G" is defined as: sG = R + eP, where e = H(R || m).
        //
        // Therefore, signatures are (R,s) that satisfy sG = R + H(R || m)P
        //
        // However, to protect against signature mutation (see "Key Prefixing" below), the hashing function of sG is
        //  modified to include the public key as well.
        //
        // With this alteration, sG becomes: R + H(R || P || m)P
        //  "R || P || m" are their byte-encoded values.
        //  Where R is encoded only with its X coordinate (64 bytes).
        //

        // Key Prefixing: (https://github.com/sipa/bips/blob/bip-schnorr/bip-schnorr.mediawiki)
        //  When using the verification rule above directly, it is possible for a third party to convert a signature (R,s)
        //  for key P into a signature (R,s + aH(R || m)) for key P + aG and the same message, for any integer a. This is
        //  not a concern for Bitcoin currently, as all signature hashes indirectly commit to the public keys. However,
        //  this may change with proposals such as SIGHASH_NOINPUT (BIP 118), or when the signature scheme is used for
        //  other purposes—especially in combination with schemes like BIP32's unhardened derivation. To combat this, we
        //  choose key prefixed Schnorr signatures; changing the equation to sG = R + H(R || P || m)P.

        // sG = R + H(R || P || m)P

        final ECPoint P = Secp256k1.CURVE.decodePoint(publicKey.compress().getBytes());
        if (P == null) { return false; }
        if (P.isInfinity()) { return false; }

        final BigInteger r = new BigInteger(1, signature.getR().getBytes());
        if (r.compareTo(CURVE_P) >= 0) { return false; }

        final BigInteger s = new BigInteger(1, signature.getS().getBytes());
        if (s.compareTo(CURVE_N) >= 0) { return false; }

        final BigInteger e;
        {
            final ByteArrayBuilder hashPreImageBuilder = new ByteArrayBuilder();
            hashPreImageBuilder.appendBytes(signature.getR());
            hashPreImageBuilder.appendBytes(P.getEncoded(true));
            hashPreImageBuilder.appendBytes(message);
            e = new BigInteger(1, BitcoinUtil.sha256(hashPreImageBuilder.build())).mod(CURVE_N);
        }

        final ECPoint sG = CURVE_POINT_G.multiply(s);
        final ECPoint eP = P.multiply(e);
        final ECPoint R = sG.add(eP.negate()).normalize(); // R = sG - eP
        if (R.isInfinity()) { return false; }

        if (! _jacobi(R.getYCoord().toBigInteger()).equals(BigInteger.ONE)) { return false; }

        return R.getXCoord().toBigInteger().equals(r);
    }
}
