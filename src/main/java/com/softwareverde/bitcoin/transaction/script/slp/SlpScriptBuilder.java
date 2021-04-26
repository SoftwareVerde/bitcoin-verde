package com.softwareverde.bitcoin.transaction.script.slp;

import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.locking.MutableLockingScript;
import com.softwareverde.bitcoin.transaction.script.opcode.ControlOperation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.slp.genesis.SlpGenesisScript;
import com.softwareverde.bitcoin.transaction.script.slp.mint.SlpMintScript;
import com.softwareverde.bitcoin.transaction.script.slp.send.SlpSendScript;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.ImmutableByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;

import java.math.BigInteger;

public class SlpScriptBuilder {
    protected static final ByteArray EMPTY_BYTE_ARRAY = new ImmutableByteArray(new byte[0]);

    // All SLP integer values are unsigned and use big-endian encoding.
    protected static ByteArray longToBytes(final Long value) {
        // if (value == 0L) { return new MutableByteArray(0); }
        if (value == 0L) { return new MutableByteArray(1); } // SLP uses non-minimally encoded values for zero.

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
            fixedLengthBytes.setByte(index, b);
        }
        return fixedLengthBytes;
    }

    protected static ByteArray bigIntegerToFixedBytes(final BigInteger value, final Integer byteCount) {
        if (value == null) {
            return new MutableByteArray(byteCount);
        }
        final byte[] bigIntegerBytes = value.toByteArray();
        final MutableByteArray outputBytes = new MutableByteArray(byteCount);
        // extract the last byteCount bytes from the big integer byte array
        for (int i=0; i<byteCount; i++) {
            final int bigIntegerByteIndex = bigIntegerBytes.length - i - 1;
            if (bigIntegerByteIndex < 0) {
                break;
            }
            outputBytes.setByte(byteCount - i - 1, bigIntegerBytes[bigIntegerByteIndex]);
        }
        return outputBytes;
    }

    public LockingScript createGenesisScript(final SlpGenesisScript slpGenesisScript) {
        // Allowed Non-Return Opcodes:
        //  PUSH_DATA                           (0x01, 0x4B),
        //  PUSH_DATA_BYTE                      (0x4C),
        //  PUSH_DATA_SHORT                     (0x4D),
        //  PUSH_DATA_INTEGER                   (0x4E)

        final ByteArray tokenAbbreviationBytes = MutableByteArray.wrap(StringUtil.stringToBytes(slpGenesisScript.getTokenAbbreviation()));
        final ByteArray tokenFullNameBytes = MutableByteArray.wrap(StringUtil.stringToBytes(slpGenesisScript.getTokenName()));
        final ByteArray documentUrlBytes = MutableByteArray.wrap(StringUtil.stringToBytes(Util.coalesce(slpGenesisScript.getDocumentUrl(), "")));
        final ByteArray documentHashBytes = Util.coalesce(slpGenesisScript.getDocumentHash(), EMPTY_BYTE_ARRAY);

        final Integer batonOutputIndex = slpGenesisScript.getBatonOutputIndex();
        final ByteArray tokenDecimalBytes = SlpScriptBuilder.longToBytes(Util.coalesce(slpGenesisScript.getDecimalCount()).longValue());
        final ByteArray batonOutputIndexBytes = (batonOutputIndex == null ? EMPTY_BYTE_ARRAY : SlpScriptBuilder.longToBytes(batonOutputIndex.longValue()));
        final ByteArray totalCountCountBytes = SlpScriptBuilder.bigIntegerToFixedBytes(slpGenesisScript.getTokenCount(), 8);

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
        lockingScript.addOperation(PushOperation.pushBytes(batonOutputIndexBytes));             // Baton Output
        lockingScript.addOperation(PushOperation.pushBytes(totalCountCountBytes));              // Mint Quantity
        return lockingScript;
    }

    public LockingScript createMintScript(final SlpMintScript slpMintScript) {
        final Integer batonOutputIndex = slpMintScript.getBatonOutputIndex();
        final ByteArray batonOutputIndexBytes = (batonOutputIndex == null ? EMPTY_BYTE_ARRAY : SlpScriptBuilder.longToBytes(batonOutputIndex.longValue()));
        final ByteArray totalCountCountBytes = SlpScriptBuilder.bigIntegerToFixedBytes(slpMintScript.getTokenCount(), 8);

        final MutableLockingScript lockingScript = new MutableLockingScript();
        lockingScript.addOperation(ControlOperation.RETURN);
        lockingScript.addOperation(PushOperation.pushBytes(SlpScriptType.LOKAD_ID));            // Lokad Id (Static Value)
        lockingScript.addOperation(PushOperation.pushBytes(SlpScriptType.TOKEN_TYPE));          // Token Type (Static Value)
        lockingScript.addOperation(PushOperation.pushBytes(SlpScriptType.MINT.getBytes()));     // Script Type (Static Value)
        lockingScript.addOperation(PushOperation.pushBytes(slpMintScript.getTokenId()));        // Token id
        lockingScript.addOperation(PushOperation.pushBytes(batonOutputIndexBytes));             // Baton Output
        lockingScript.addOperation(PushOperation.pushBytes(totalCountCountBytes));              // Mint Quantity
        return lockingScript;
    }

    public LockingScript createSendScript(final SlpSendScript slpSendScript) {
        final MutableLockingScript lockingScript = new MutableLockingScript();
        lockingScript.addOperation(ControlOperation.RETURN);
        lockingScript.addOperation(PushOperation.pushBytes(SlpScriptType.LOKAD_ID));            // Lokad Id (Static Value)
        lockingScript.addOperation(PushOperation.pushBytes(SlpScriptType.TOKEN_TYPE));          // Token Type (Static Value)
        lockingScript.addOperation(PushOperation.pushBytes(SlpScriptType.SEND.getBytes()));     // Script Type (Static Value)
        lockingScript.addOperation(PushOperation.pushBytes(slpSendScript.getTokenId()));        // Token id

        for (int i = 1; i < SlpSendScript.MAX_OUTPUT_COUNT; ++i) {
            final BigInteger amount = slpSendScript.getAmount(i);
            if (amount == null) { break; }

            final ByteArray spendAmountBytes = SlpScriptBuilder.bigIntegerToFixedBytes(amount, 8);
            lockingScript.addOperation(PushOperation.pushBytes(spendAmountBytes));
        }

        return lockingScript;
    }
}
