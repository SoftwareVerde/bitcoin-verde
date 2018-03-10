package com.softwareverde.bitcoin.secp256k1.signature;

import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.bitcoin.type.bytearray.ByteArray;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import org.junit.Test;

public class SignatureTests {
    @Test
    public void should_serialize_and_deserialize() {
        // Setup
        final byte[] r = BitcoinUtil.hexStringToByteArray("00CF4D7571DD47A4D47F5CB767D54D6702530A3555726B27B6AC56117F5E7808FE");
        final byte[] s = BitcoinUtil.hexStringToByteArray("008CBB42233BB04D7F28A715CF7C938E238AFDE90207E9D103DD9018E12CB7180E");
        final Signature signature = new Signature(r, s);

        // Action
        final ByteArray asDer = signature.encodeAsDer();
        final Signature signatureCopy = Signature.fromBytes(asDer.getBytes());

        // Assert
        TestUtil.assertEqual(r, signatureCopy.getR());
        TestUtil.assertEqual(s, signatureCopy.getS());
    }
}
