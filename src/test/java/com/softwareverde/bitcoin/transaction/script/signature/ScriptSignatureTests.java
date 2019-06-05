package com.softwareverde.bitcoin.transaction.script.signature;

import com.softwareverde.constable.bytearray.ByteArray;
import org.junit.Assert;
import org.junit.Test;

public class ScriptSignatureTests {
    @Test
    public void should_inflate_signature_from_20190515HF() {
        // Script Signature has hash type byte provided...
        final ByteArray byteArray = ByteArray.fromHexString("BD49BBF18F6EFD604AD8EE21A7C361561618E54B0C59B8F1C442FBBB8255CE9FF36160C0551DD6E1CC6A95BE43D628E6A186A7B8C391F32A57CE27E3AF5A3A9D41");
        final ScriptSignature scriptSignature = ScriptSignature.fromBytes(byteArray);

        Assert.assertNotNull(scriptSignature);
    }
}
