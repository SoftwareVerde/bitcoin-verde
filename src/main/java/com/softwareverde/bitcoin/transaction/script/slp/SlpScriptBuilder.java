package com.softwareverde.bitcoin.transaction.script.slp;

import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.locking.MutableLockingScript;
import com.softwareverde.bitcoin.transaction.script.opcode.ControlOperation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.slp.genesis.SlpGenesisScript;
import com.softwareverde.bitcoin.transaction.script.slp.mint.SlpMintScript;
import com.softwareverde.bitcoin.transaction.script.slp.send.SlpSendScript;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.StringUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.Util;

public class SlpScriptBuilder {
    // All SLP integer values are unsigned and use big-endian encoding.
    protected static ByteArray longToBytes(final Long value) {
        if (value == 0L) { return new MutableByteArray(0); }
        final MutableByteArray longBytes = MutableByteArray.wrap(ByteUtil.longToBytes(value));
        final int trimByteCount = (Long.numberOfLeadingZeros(value) / 8);
        return MutableByteArray.wrap(longBytes.getBytes(trimByteCount, (8 - trimByteCount)));
    }

    protected static ByteArray longToFixedBytes(final Long value, final Integer byteCount) {
        final MutableByteArray fixedLengthBytes = new MutableByteArray(byteCount);
        final ByteArray longBytes = MutableByteArray.wrap(ByteUtil.longToBytes(value));
        for (int i = 0; i < byteCount; ++i) {
            int index = (byteCount - i - 1);
            final byte b = longBytes.getByte(index);
            fixedLengthBytes.set(index, b);
        }
        return fixedLengthBytes;
    }

    public LockingScript createGenesisScript(final SlpGenesisScript slpGenesisScript) {
        // Allowed Non-Return Opcodes:
        //  PUSH_DATA                           (0x01, 0x4B),
        //  PUSH_DATA_BYTE                      (0x4C),
        //  PUSH_DATA_SHORT                     (0x4D),
        //  PUSH_DATA_INTEGER                   (0x4E)

        final ByteArray tokenAbbreviationBytes = MutableByteArray.wrap(StringUtil.stringToBytes(slpGenesisScript.getTokenAbbreviation()));
        final ByteArray tokenFullNameBytes = MutableByteArray.wrap(StringUtil.stringToBytes(slpGenesisScript.getTokenName()));
        final ByteArray documentUrlBytes = MutableByteArray.wrap(StringUtil.stringToBytes(slpGenesisScript.getDocumentUrl()));
        final ByteArray documentHashBytes = Util.coalesce(slpGenesisScript.getDocumentHash(), new MutableByteArray(0));

        final ByteArray tokenDecimalBytes = SlpScriptBuilder.longToBytes(Util.coalesce(slpGenesisScript.getDecimalCount()).longValue());
        final ByteArray generatorOutputIndexBytes = SlpScriptBuilder.longToBytes(Util.coalesce(slpGenesisScript.getGeneratorOutputIndex()).longValue());
        final ByteArray totalCountCountBytes = SlpScriptBuilder.longToFixedBytes(Util.coalesce(slpGenesisScript.getTokenCount()), 8);

        final MutableLockingScript lockingScript = new MutableLockingScript();
        lockingScript.addOperation(ControlOperation.RETURN);
        lockingScript.addOperation(PushOperation.pushBytes(SlpScriptType.LOKAD_ID));            // Lokad Id (Static Value)
        lockingScript.addOperation(PushOperation.pushBytes(SlpScriptType.TOKEN_TYPE));          // Token Type (Static Value)
        lockingScript.addOperation(PushOperation.pushBytes(SlpScriptType.GENESIS.getBytes()));  // Script Type (Static Value)
        lockingScript.addOperation(PushOperation.pushBytes(tokenAbbreviationBytes));            // Token Abbreviation
        lockingScript.addOperation(PushOperation.pushBytes(tokenFullNameBytes));                // Token Full Name
        lockingScript.addOperation(PushOperation.pushBytes(documentUrlBytes));                  // Token Document Url
        lockingScript.addOperation(PushOperation.pushBytes(documentHashBytes));                 // Document Hash
        lockingScript.addOperation(PushOperation.pushBytes(tokenDecimalBytes));                 // Decimal Count
        lockingScript.addOperation(PushOperation.pushBytes(generatorOutputIndexBytes));         // Generator Output
        lockingScript.addOperation(PushOperation.pushBytes(totalCountCountBytes));              // Mint Quantity
        return lockingScript;
    }

    public LockingScript createMintScript(final SlpMintScript slpMintScript) {
        final ByteArray generatorOutputIndexBytes = SlpScriptBuilder.longToBytes(Util.coalesce(slpMintScript.getGeneratorOutputIndex()).longValue());
        final ByteArray totalCountCountBytes = SlpScriptBuilder.longToFixedBytes(Util.coalesce(slpMintScript.getTokenCount()), 8);

        final MutableLockingScript lockingScript = new MutableLockingScript();
        lockingScript.addOperation(ControlOperation.RETURN);
        lockingScript.addOperation(PushOperation.pushBytes(SlpScriptType.LOKAD_ID));            // Lokad Id (Static Value)
        lockingScript.addOperation(PushOperation.pushBytes(SlpScriptType.TOKEN_TYPE));          // Token Type (Static Value)
        lockingScript.addOperation(PushOperation.pushBytes(SlpScriptType.MINT.getBytes()));     // Script Type (Static Value)
        lockingScript.addOperation(PushOperation.pushBytes(slpMintScript.getTokenId()));        // Token id
        lockingScript.addOperation(PushOperation.pushBytes(generatorOutputIndexBytes));         // Generator Output
        lockingScript.addOperation(PushOperation.pushBytes(totalCountCountBytes));              // Mint Quantity
        return lockingScript;
    }

    public LockingScript createSendScript(final SlpSendScript slpSendScript) {
        final MutableLockingScript lockingScript = new MutableLockingScript();
        lockingScript.addOperation(ControlOperation.RETURN);
        lockingScript.addOperation(PushOperation.pushBytes(SlpScriptType.LOKAD_ID));            // Lokad Id (Static Value)
        lockingScript.addOperation(PushOperation.pushBytes(SlpScriptType.TOKEN_TYPE));          // Token Type (Static Value)
        lockingScript.addOperation(PushOperation.pushBytes(SlpScriptType.SEND.getBytes()));     // Script Type (Static Value)
        lockingScript.addOperation(PushOperation.pushBytes(slpSendScript.getTokenId()));       // Token id

        for (int i = 1; i < SlpSendScript.MAX_OUTPUT_COUNT; ++i) {
            final Long amount = slpSendScript.getAmount(i);
            if (amount == null) { break; }

            final ByteArray spendAmountBytes = SlpScriptBuilder.longToFixedBytes(amount, 8);
            lockingScript.addOperation(PushOperation.pushBytes(spendAmountBytes));
        }

        return lockingScript;
    }
}
