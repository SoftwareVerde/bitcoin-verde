package com.softwareverde.bitcoin.secp256k1;

import com.softwareverde.bitcoin.secp256k1.key.PublicKey;
import com.softwareverde.bitcoin.secp256k1.signature.Signature;
import com.softwareverde.util.HexUtil;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;

import java.security.Security;

public class Schnorr {
    public static final byte[] CURVE_P;

    protected static final ECCurve CURVE;
    protected static final ECPoint CURVE_POINT_G;
    public static final ECDomainParameters CURVE_DOMAIN;

    static {
        Security.addProvider(new BouncyCastleProvider());

        final ECNamedCurveParameterSpec curveParameterSpec = ECNamedCurveTable.getParameterSpec("secp256k1");
        CURVE_POINT_G = curveParameterSpec.getG();
        CURVE = curveParameterSpec.getCurve();
        CURVE_DOMAIN =  new ECDomainParameters(CURVE, CURVE_POINT_G, curveParameterSpec.getN());

        CURVE_P = HexUtil.hexStringToByteArray("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F");
    }

    // https://github.com/sipa/bips/blob/bip-schnorr/bip-schnorr.mediawiki
    // https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/2019-05-15-schnorr.md
    public static Boolean verifySignature(final Signature signature, final PublicKey publicKey, final byte[] message) {

        // Schnorr Signatures, for message ("m") and public key ("P"), involve a point ("R"),
        //      integers ("e") and ("s") (picked by the signer), and generator G.
        //
        // "H" is the Sha256 hashing function, and "||" indicates concatenation.
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

        return false;
    }
}
