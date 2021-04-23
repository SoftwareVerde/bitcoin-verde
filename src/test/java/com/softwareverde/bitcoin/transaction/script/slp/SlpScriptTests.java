package com.softwareverde.bitcoin.transaction.script.slp;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.script.ScriptDeflater;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.slp.genesis.MutableSlpGenesisScript;
import com.softwareverde.bitcoin.transaction.script.slp.genesis.SlpGenesisScript;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.util.ByteUtil;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

public class SlpScriptTests {

    @Test
    public void should_generate_genesis_token() throws Exception {
        // Assert
        final ScriptDeflater scriptDeflater = new ScriptDeflater();
        final SlpScriptBuilder slpScriptBuilder = new SlpScriptBuilder();
        final SlpScriptInflater slpScriptInflater = new SlpScriptInflater();

        final TransactionInflater transactionInflater = new TransactionInflater();
        // Tx: 54856D53EE81C1EDF9C8AA0311F66FA4DC348213D47FB3E28D7873AC008F7E26
        final Transaction slpGenesisTransaction = transactionInflater.fromBytes(ByteArray.fromHexString("0100000001ECFACC1990F40F68538E0072B6A579D372CF47FCE794B502C37578485AE73192000000006A473044022040177413F9C11C234584EA6FEF76F5978D259054C5935F03DD0B26D411295AFC02204B99B05F2B7E2F7E68CE1AAC54AA5F2F53C8102A88F16DC8AAE0B7D846E9B4864121030560CE41F8FF3875A60C17D58A2B2B532C41B53F7C885BB047AEFDCAF7997B39FEFFFFFF030000000000000000486A04534C500001010747454E4553495303534C5009534C5020546F6B656E1A68747470733A2F2F73696D706C656C65646765722E636173682F4C0001084C0008000775F05A07400022020000000000001976A9142B305BB7C3331464F20CE44AFE1D2485C44CD1CA88ACB2530000000000001976A9142B305BB7C3331464F20CE44AFE1D2485C44CD1CA88AC914F0800"));

        final LockingScript expectedLockingScript = slpGenesisTransaction.getTransactionOutputs().get(0).getLockingScript();

        final MutableSlpGenesisScript slpGenesisScript = new MutableSlpGenesisScript();
        slpGenesisScript.setTokenName("SLP Token");
        slpGenesisScript.setTokenAbbreviation("SLP");
        slpGenesisScript.setDocumentUrl("https://simpleledger.cash/");
        slpGenesisScript.setDocumentHash(null);
        slpGenesisScript.setDecimalCount(8);
        slpGenesisScript.setBatonOutputIndex(null);
        slpGenesisScript.setTokenCount(BigInteger.valueOf(21000000L * Transaction.SATOSHIS_PER_BITCOIN));

        // Action
        final LockingScript lockingScript = slpScriptBuilder.createGenesisScript(slpGenesisScript);
        final SlpScriptType slpScriptType = slpScriptInflater.getScriptType(lockingScript);

        // Assert
        Assert.assertEquals(scriptDeflater.toBytes(expectedLockingScript), scriptDeflater.toBytes(lockingScript));
        Assert.assertEquals(slpScriptInflater.genesisScriptFromScript(expectedLockingScript), slpScriptInflater.genesisScriptFromScript(lockingScript));
        Assert.assertEquals(SlpScriptType.GENESIS, slpScriptType);
    }

    @Test
    public void decimal_count_byte_length_must_be_1_byte_for_zero_decimals() {
        final MutableSlpGenesisScript slpGenesisScript = new MutableSlpGenesisScript();
        slpGenesisScript.setTokenName("Bitcoin Cash");
        slpGenesisScript.setTokenCount(new BigInteger(1, ByteUtil.longToBytes(0xFFFFFFFFFFFFFFFFL))); // 2^64-1
        slpGenesisScript.setBatonOutputIndex(null);
        slpGenesisScript.setTokenAbbreviation("BCH");
        slpGenesisScript.setDocumentUrl(null);
        slpGenesisScript.setDocumentHash(null);
        slpGenesisScript.setDecimalCount(0);

        final SlpScriptBuilder slpScriptBuilder = new SlpScriptBuilder();
        final LockingScript genesisLockingScript = slpScriptBuilder.createGenesisScript(slpGenesisScript);

        final SlpScriptInflater slpScriptInflater = new SlpScriptInflater();
        final SlpGenesisScript inflatedSlpGenesisScript = slpScriptInflater.genesisScriptFromScript(genesisLockingScript);

        Assert.assertEquals(slpGenesisScript, inflatedSlpGenesisScript);
    }
}
