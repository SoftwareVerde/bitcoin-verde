package com.softwareverde.bitcoin.secp256k1;

import com.softwareverde.bitcoin.jni.NativeSecp256k1;
import com.softwareverde.bitcoin.secp256k1.key.PublicKey;
import com.softwareverde.bitcoin.secp256k1.signature.Signature;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.Security;

public class Secp256k1 {
    public static final byte[] CURVE_P;

    protected static final ECCurve CURVE;
    protected static final ECPoint CURVE_POINT_G;
    public static final ECDomainParameters CURVE_DOMAIN;

    static {
        Security.addProvider(new BouncyCastleProvider());

        final String SECP256K1 = "secp256k1";
        final ECNamedCurveParameterSpec curveParameterSpec = ECNamedCurveTable.getParameterSpec(SECP256K1);
        CURVE_POINT_G = curveParameterSpec.getG();
        CURVE = curveParameterSpec.getCurve();
        CURVE_DOMAIN =  new ECDomainParameters(Secp256k1.CURVE, Secp256k1.CURVE_POINT_G, curveParameterSpec.getN());

        CURVE_P = HexUtil.hexStringToByteArray("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F");
    }

    public static byte[] getPublicKeyPoint(final byte[] privateKeyBytes) {
        final ECPoint pointQ = Secp256k1.CURVE_POINT_G.multiply(new BigInteger(1, privateKeyBytes));
        return pointQ.getEncoded();
    }

    public static ByteArray getPublicKeyPoint(final ByteArray privateKey) {
        final ECPoint pointQ = Secp256k1.CURVE_POINT_G.multiply(new BigInteger(1, privateKey.getBytes()));
        return MutableByteArray.wrap(pointQ.getEncoded());
    }

    protected static Boolean _verifySignatureViaBouncyCastle(final Signature signature, final PublicKey publicKey, final byte[] message) {
        final ECPublicKeyParameters publicKeyParameters;
        {
            final ECPoint publicKeyPoint = Secp256k1.CURVE.decodePoint(publicKey.getBytes());
            publicKeyParameters = new ECPublicKeyParameters(publicKeyPoint, Secp256k1.CURVE_DOMAIN);
        }

        final ECDSASigner signer = new ECDSASigner();
        signer.init(false, publicKeyParameters);

        try {
            return signer.verifySignature(message, new BigInteger(1, signature.getR().getBytes()), new BigInteger(1, signature.getS().getBytes()));
        }
        catch (final Exception exception) {
            // NOTE: Bouncy Castle contains/contained a bug that would crash during certain specially-crafted malicious signatures.
            //  Instead of crashing, the signature is instead just marked as invalid.
            Logger.log(exception);
            return false;
        }
    }

    protected static Boolean _verifySignatureViaJni(final Signature signature, final PublicKey publicKey, final byte[] message) {
        try {
            return NativeSecp256k1.verify(message, signature.asCanonical().encodeAsDer().getBytes(), publicKey.getBytes());
        }
        catch (Exception e) {
            Logger.log(e);
            return false;
        }
    }

    public static Boolean verifySignature(final Signature signature, final PublicKey publicKey, final byte[] message) {
        if (NativeSecp256k1.isEnabled()) {
            return _verifySignatureViaJni(signature, publicKey, message);
        }

        // Fallback to BouncyCastle if the libsecp256k1 failed to load for this architecture...
        return _verifySignatureViaBouncyCastle(signature, publicKey, message);
    }

    public static Signature sign(final byte[] privateKey, final byte[] message) {
        final ECPrivateKeyParameters privateKeyParameters;
        {
            final BigInteger privateKeyBigInteger = new BigInteger(1, privateKey);
            privateKeyParameters = new ECPrivateKeyParameters(privateKeyBigInteger, Secp256k1.CURVE_DOMAIN);
        }

        final ECDSASigner signer = new ECDSASigner();
        signer.init(true, privateKeyParameters);

        final BigInteger r;
        final BigInteger s;
        {
            final BigInteger[] signatureIntegers = signer.generateSignature(message);
            r = signatureIntegers[0];
            s = signatureIntegers[1];
        }

        final byte[] rBytes = r.toByteArray();
        final byte[] sBytes;
        { // BIP-62: Reducing Transaction Malleability: https://github.com/bitcoin/bips/blob/master/bip-0062.mediawiki#Low_S_values_in_signatures
            // Since S may be positive or negative mod N, there are two valid signatures to a message.
            // In order to help eliminate transaction malleability, by convention, the S value will always be
            //  transformed to be the lower of its two possible values.
            // For instance, assume N is 10, and S is 8.  Another valid value for S could be 2 (i.e. -8 mod 10 == 2).
            //  The lower S can be calculated by taking N and subtracting S (i.e. 10 - 8 = 2).
            final BigInteger n = CURVE_DOMAIN.getN();
            if (s.compareTo(n.shiftRight(1)) <= 0) {
                sBytes = s.toByteArray();
            }
            else {
                sBytes = n.subtract(s).toByteArray();
            }
        }

        return new Signature(rBytes, sBytes);
    }

    public static byte[] decompressPoint(byte[] encodedPublicKeyPoint) {
        final ECPoint decodedPoint = CURVE.decodePoint(encodedPublicKeyPoint);

        final BigInteger x = decodedPoint.getX().toBigInteger();
        final BigInteger y = decodedPoint.getY().toBigInteger();
        final ECPoint decompressedPoint = CURVE.createPoint(x, y, false);
        return decompressedPoint.getEncoded();
    }

    protected Secp256k1() { }
}
