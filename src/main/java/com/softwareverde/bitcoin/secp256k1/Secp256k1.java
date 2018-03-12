package com.softwareverde.bitcoin.secp256k1;

import com.softwareverde.bitcoin.secp256k1.signature.Signature;
import com.softwareverde.bitcoin.type.bytearray.ByteArray;
import com.softwareverde.bitcoin.type.bytearray.ImmutableByteArray;
import com.softwareverde.bitcoin.type.bytearray.MutableByteArray;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;
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
    protected static final ECDomainParameters CURVE_DOMAIN;

    static {
        Security.addProvider(new BouncyCastleProvider());

        final String SECP256K1 = "secp256k1";
        final ECNamedCurveParameterSpec curveParameterSpec = ECNamedCurveTable.getParameterSpec(SECP256K1);
        CURVE_POINT_G = curveParameterSpec.getG();
        CURVE = curveParameterSpec.getCurve();
        CURVE_DOMAIN =  new ECDomainParameters(Secp256k1.CURVE, Secp256k1.CURVE_POINT_G, curveParameterSpec.getN());

        CURVE_P = BitcoinUtil.hexStringToByteArray("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F");
    }

    public static byte[] getPublicKeyPoint(final byte[] privateKeyBytes) {
        final ECPoint pointQ = Secp256k1.CURVE_POINT_G.multiply(new BigInteger(1, privateKeyBytes));
        return pointQ.getEncoded();
    }

    public static ByteArray getPublicKeyPoint(final ByteArray privateKey) {
        final ECPoint pointQ = Secp256k1.CURVE_POINT_G.multiply(new BigInteger(1, privateKey.getBytes()));
        return MutableByteArray.wrap(pointQ.getEncoded());
    }

    public static Boolean verifySignature(final Signature signature, final byte[] publicKey, final byte[] message) {

        final ECPublicKeyParameters publicKeyParameters;
        {
            final ECPoint publicKeyPoint = Secp256k1.CURVE.decodePoint(publicKey);
            publicKeyParameters = new ECPublicKeyParameters(publicKeyPoint, Secp256k1.CURVE_DOMAIN);
        }

        final ECDSASigner signer = new ECDSASigner();
        signer.init(false, publicKeyParameters);
        return signer.verifySignature(message, new BigInteger(1, signature.getR()), new BigInteger(1, signature.getS()));
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

    protected Secp256k1() { }
}
