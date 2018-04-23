package com.softwareverde.bitcoin.secp256k1.signature;

import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.util.HexUtil;
import org.junit.Test;

public class SignatureTests {
    @Test
    public void should_serialize_and_deserialize() {
        // Setup
        final byte[] r = HexUtil.hexStringToByteArray("00CF4D7571DD47A4D47F5CB767D54D6702530A3555726B27B6AC56117F5E7808FE");
        final byte[] s = HexUtil.hexStringToByteArray("008CBB42233BB04D7F28A715CF7C938E238AFDE90207E9D103DD9018E12CB7180E");
        final Signature signature = new Signature(r, s);

        // Action
        final ByteArray asDer = signature.encodeAsDer();
        final Signature signatureCopy = Signature.fromBytes(asDer);

        // Assert
        TestUtil.assertEqual(r, signatureCopy.getR().getBytes());
        TestUtil.assertEqual(s, signatureCopy.getS().getBytes());
    }
}
