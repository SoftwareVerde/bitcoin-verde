package com.softwareverde.bitcoin.transaction.script.slp;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.opcode.Opcode;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.slp.genesis.MutableSlpGenesisScript;
import com.softwareverde.bitcoin.transaction.script.slp.genesis.SlpGenesisScript;
import com.softwareverde.bitcoin.transaction.script.slp.mint.MutableSlpMintScript;
import com.softwareverde.bitcoin.transaction.script.slp.mint.SlpMintScript;
import com.softwareverde.bitcoin.transaction.script.slp.send.MutableSlpSendScript;
import com.softwareverde.bitcoin.transaction.script.slp.send.SlpSendScript;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.StringUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.util.Util;

public class SlpScriptInflater {
    protected static Boolean _matchesSlpFormat(final LockingScript lockingScript) {
        final List<Operation> operations = lockingScript.getOperations();
        if (operations.getSize() < 5) { return false; }

        for (int i = 0; i < operations.getSize(); ++i) {
            final Operation operation = operations.get(i);

            if (i == 0) {
                final boolean firstOperationIsReturn = (operation.getOpcodeByte() == Opcode.RETURN.getValue());
                if (! firstOperationIsReturn) { return false; }
            }
            else {
                if (operation.getType() != Operation.Type.OP_PUSH) { return false; }
                final PushOperation pushOperation = (PushOperation) operation;

                //  PUSH_DATA                           (0x01, 0x4B),
                //  PUSH_DATA_BYTE                      (0x4C),
                //  PUSH_DATA_SHORT                     (0x4D),
                //  PUSH_DATA_INTEGER                   (0x4E)

                final int opcodeByte = ByteUtil.byteToInteger(pushOperation.getOpcodeByte());
                if ( (opcodeByte < 0x01) || (opcodeByte > 0x4E) ) { return false; }
            }
        }

        return true;
    }

    protected static SlpScriptType _getScriptType(final LockingScript lockingScript) {
        final List<Operation> operations = lockingScript.getOperations();
        final PushOperation scriptTypeOperation = (PushOperation) operations.get(3);

        final ByteArray scriptTypeBytes = scriptTypeOperation.getValue();
        return SlpScriptType.fromBytes(scriptTypeBytes);
    }

    protected static SlpTokenId _getTokenId(final LockingScript lockingScript) {
        final List<Operation> operations = lockingScript.getOperations();
        if (operations.getSize() < 5) { return null; }

        final PushOperation tokenPushOperation = (PushOperation) operations.get(4);
        final ByteArray tokenIdBytes = tokenPushOperation.getValue();
        return SlpTokenId.copyOf(tokenIdBytes.getBytes());
    }

    public static Boolean matchesSlpFormat(final LockingScript lockingScript) {
        return _matchesSlpFormat(lockingScript);
    }

    public static SlpScriptType getScriptType(final LockingScript lockingScript) {
        if (! _matchesSlpFormat(lockingScript)) { return null; }
        return _getScriptType(lockingScript);
    }

    public static SlpTokenId getTokenId(final LockingScript lockingScript) {
        if (! _matchesSlpFormat(lockingScript)) { return null; }
        return _getTokenId(lockingScript);
    }

    public SlpGenesisScript genesisScriptFromScript(final LockingScript lockingScript) {
        if (! _matchesSlpFormat(lockingScript)) { return null; }
        final SlpScriptType slpScriptType = _getScriptType(lockingScript);
        if (! Util.areEqual(SlpScriptType.GENESIS, slpScriptType)) { return null; }

        final List<Operation> operations = lockingScript.getOperations();
        if (operations.getSize() != 11) { return null; }

        final MutableSlpGenesisScript slpGenesisScript = new MutableSlpGenesisScript();

        final Value tokenAbbreviationValue = ((PushOperation) operations.get(4)).getValue();
        slpGenesisScript.setTokenAbbreviation(StringUtil.bytesToString(tokenAbbreviationValue.getBytes()));

        final Value tokenNameValue = ((PushOperation) operations.get(5)).getValue();
        slpGenesisScript.setTokenName(StringUtil.bytesToString(tokenNameValue.getBytes()));

        final Value tokenDocumentUrlValue = ((PushOperation) operations.get(6)).getValue();
        slpGenesisScript.setDocumentUrl(StringUtil.bytesToString(tokenDocumentUrlValue.getBytes()));

        final Value tokenDocumentHashValue = ((PushOperation) operations.get(7)).getValue();
        if ( (tokenDocumentHashValue.getByteCount() != 0) && (tokenDocumentHashValue.getByteCount() != Sha256Hash.BYTE_COUNT) ) { return null; }
        slpGenesisScript.setDocumentHash(Sha256Hash.copyOf(tokenDocumentHashValue.getBytes()));

        final Value tokenDecimalValue = ((PushOperation) operations.get(8)).getValue();
        final Integer decimalCount = ByteUtil.bytesToInteger(tokenDecimalValue.getBytes());
        if ( (decimalCount < 0) || (decimalCount > 9) ) { return null; }
        slpGenesisScript.setDecimalCount(decimalCount);

        final Value generatorOutputIndexValue = ((PushOperation) operations.get(9)).getValue();
        if (generatorOutputIndexValue.getByteCount() > 1) { return null; }
        final Integer generatorOutputIndex = ByteUtil.bytesToInteger(generatorOutputIndexValue.getBytes());
        if (generatorOutputIndexValue.getByteCount() == 1) {
            if (generatorOutputIndex < 2) { return null; }
        }
        slpGenesisScript.setGeneratorOutputIndex(generatorOutputIndex);

        final Value totalTokenCountValue = ((PushOperation) operations.get(10)).getValue();
        if (totalTokenCountValue.getByteCount() != 8) { return null; }
        slpGenesisScript.setTokenCount(ByteUtil.bytesToLong(totalTokenCountValue.getBytes()));

        return slpGenesisScript;
    }

    public SlpMintScript mintScriptFromScript(final LockingScript lockingScript) {
        if (! _matchesSlpFormat(lockingScript)) { return null; }
        final SlpScriptType slpScriptType = _getScriptType(lockingScript);
        if (! Util.areEqual(SlpScriptType.MINT, slpScriptType)) { return null; }

        final SlpTokenId tokenId = _getTokenId(lockingScript);
        if (tokenId == null) { return null; }

        final MutableSlpMintScript slpMintScript = new MutableSlpMintScript();
        slpMintScript.setTokenId(tokenId);

        final List<Operation> operations = lockingScript.getOperations();
        if (operations.getSize() != 7) { return null; }

        final Value generatorOutputIndexValue = ((PushOperation) operations.get(5)).getValue();
        if (generatorOutputIndexValue.getByteCount() > 1) { return null; }
        final Integer generatorOutputIndex = ByteUtil.bytesToInteger(generatorOutputIndexValue.getBytes());
        if (generatorOutputIndexValue.getByteCount() == 1) {
            if (generatorOutputIndex < 2) { return null; }
        }
        slpMintScript.setGeneratorOutputIndex(generatorOutputIndex);

        final Value totalTokenCountValue = ((PushOperation) operations.get(6)).getValue();
        if (totalTokenCountValue.getByteCount() != 8) { return null; }
        slpMintScript.setTokenCount(ByteUtil.bytesToLong(totalTokenCountValue.getBytes()));

        return slpMintScript;
    }

    public SlpSendScript sendScriptFromScript(final LockingScript lockingScript) {
        if (! _matchesSlpFormat(lockingScript)) { return null; }
        final SlpScriptType slpScriptType = _getScriptType(lockingScript);
        if (! Util.areEqual(SlpScriptType.SEND, slpScriptType)) { return null; }

        final SlpTokenId tokenId = _getTokenId(lockingScript);
        if (tokenId == null) { return null; }

        final MutableSlpSendScript slpSendScript = new MutableSlpSendScript();
        slpSendScript.setTokenId(tokenId);

        int transactionOutputIndex = 1;
        final List<Operation> operations = lockingScript.getOperations();
        for (int i = 5; i < operations.getSize(); ++i) {
            final PushOperation operation = (PushOperation) operations.get(i);
            final ByteArray value = operation.getValue();
            final Long amount = ByteUtil.bytesToLong(value.getBytes());
            slpSendScript.setAmount(transactionOutputIndex, amount);
            transactionOutputIndex += 1;
        }

        return slpSendScript;
    }
}
