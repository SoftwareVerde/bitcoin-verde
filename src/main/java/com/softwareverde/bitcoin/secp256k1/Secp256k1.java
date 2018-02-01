package com.softwareverde.bitcoin.secp256k1;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.Security;

public class Secp256k1 {
    static {
        Security.addProvider( new BouncyCastleProvider() );
    }

    public static byte[] getPublicKeyPoint(byte[] privateKey) {
        try {
            final ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256k1");
            final ECPoint pointQ = spec.getG().multiply(new BigInteger(1, privateKey));

            return pointQ.getEncoded();
        }
        catch (final Exception exception) {
            exception.printStackTrace();
            return null;
        }
    }
}
