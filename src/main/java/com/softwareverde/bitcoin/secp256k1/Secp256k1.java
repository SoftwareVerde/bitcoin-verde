package com.softwareverde.bitcoin.secp256k1;

import com.softwareverde.bitcoin.secp256k1.signature.Signature;
import org.bouncycastle.crypto.params.ECDomainParameters;
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
    }

    public static byte[] getPublicKeyPoint(byte[] privateKey) {
        final ECPoint pointQ = Secp256k1.CURVE_POINT_G.multiply(new BigInteger(1, privateKey));
        return pointQ.getEncoded();
    }

    public static Boolean verifySignature(final Signature signature, byte[] publicKey, byte[] message) {

        final ECPublicKeyParameters publicKeyParameters;
        {
            final ECPoint publicKeyPoint = Secp256k1.CURVE.decodePoint(publicKey);
            publicKeyParameters = new ECPublicKeyParameters(publicKeyPoint, Secp256k1.CURVE_DOMAIN);
        }

        final ECDSASigner signer = new ECDSASigner();
        signer.init(false, publicKeyParameters);
        return signer.verifySignature(message, new BigInteger(1, signature.getR()), new BigInteger(1, signature.getS()));
    }

    protected Secp256k1() { }
}
