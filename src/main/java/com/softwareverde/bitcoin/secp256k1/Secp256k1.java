package com.softwareverde.bitcoin.secp256k1;

import com.softwareverde.bitcoin.secp256k1.signature.Signature;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DERObject;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;

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
//        final X9ECParameters curve = SECNamedCurves.getByName(SECP256K1);
//        final ECDomainParameters domainParameters = new ECDomainParameters(curve.getCurve(), curve.getG(), curve.getN(), curve.getH());

//        final ECNamedCurveParameterSpec curveParameterSpec = ECNamedCurveTable.getParameterSpec(SECP256K1);
//        final ECCurve curve = curveParameterSpec.getCurve();
//        final ECDomainParameters domain = new ECDomainParameters(curve, curveParameterSpec.getG(), curveParameterSpec.getN());
//        final ECPublicKeyParameters publicKeyParams = new ECPublicKeyParameters(curve.decodePoint(publicKey), domain);
//
//        final ECDSASigner signer = new ECDSASigner();
//        signer.init(false, publicKeyParams);
//        return signer.verifySignature(message, new BigInteger(1, signature.getR()), new BigInteger(1, signature.getS()));

//        final SecureRandom secureRandom = new SecureRandom();
        final X9ECParameters curve = SECNamedCurves.getByName(SECP256K1);
        final ECDomainParameters domain = new ECDomainParameters(curve.getCurve(), curve.getG(), curve.getN(), curve.getH());

//        ASN1InputStream asn1 = new ASN1InputStream(signature.encodeAsDer());
//        try {
            final ECDSASigner signer = new ECDSASigner();
            signer.init(false, new ECPublicKeyParameters(curve.getCurve().decodePoint(publicKey), domain));
            return signer.verifySignature(message, new BigInteger(1, signature.getR()), new BigInteger(1, signature.getS()));

//            final BigInteger r = (new DERInteger(signature.getR())).getPositiveValue();
//            final BigInteger s = (new DERInteger(signature.getS())).getPositiveValue();
//            DLSequence seq = (DLSequence) asn1.readObject ();
//            BigInteger r = ((ASN1Integer) seq.getObjectAt (0)).getPositiveValue ();
//            BigInteger s = ((ASN1Integer) seq.getObjectAt (1)).getPositiveValue ();
            // return signer.verifySignature(message, r, s);
//        }
//        catch (final Exception exception) {
//            return false;
//        }
//        finally
//        {
//            try
//            {
//                asn1.close ();
//            }
//            catch ( IOException e )
//            {
//            }
//        }


//        try {
//            final java.security.Signature ecdsaSignature = java.security.Signature.getInstance("SHA256withECDSA", "BC");
//            final PublicKey ecdsaPublicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKey));
//            ecdsaSignature.initVerify(ecdsaPublicKey);
//            ecdsaSignature.update(message);
//            return ecdsaSignature.verify(signature.encodeAsDer());
//        }
//        catch (final Exception exception) {
//            throw new RuntimeException(exception);
////            return false;
//        }
    }

    protected Secp256k1() { }
}
