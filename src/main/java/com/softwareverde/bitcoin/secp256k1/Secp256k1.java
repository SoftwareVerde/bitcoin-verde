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
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static final String SECP256K1 = "secp256k1";

    public static byte[] getPublicKeyPoint(byte[] privateKey) {
        final ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec(SECP256K1);
        final ECPoint pointQ = spec.getG().multiply(new BigInteger(1, privateKey));

        return pointQ.getEncoded();
    }

    public static Boolean verifySignature(final Signature signature, byte[] publicKey, byte[] message) {
        final ECNamedCurveParameterSpec curveParameterSpec = ECNamedCurveTable.getParameterSpec(SECP256K1);
        final ECCurve curve = curveParameterSpec.getCurve();
        final ECDomainParameters domain = new ECDomainParameters(curve, curveParameterSpec.getG(), curveParameterSpec.getN());
        final ECPublicKeyParameters publicKeyParams = new ECPublicKeyParameters(curve.decodePoint(publicKey), domain);

        final ECDSASigner signer = new ECDSASigner();
        signer.init(false, publicKeyParams);
        return signer.verifySignature(message, new BigInteger(1, signature.getR()), new BigInteger(1, signature.getS()));
    }

    protected Secp256k1() { }
}
