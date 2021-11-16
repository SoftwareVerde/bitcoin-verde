package com.softwareverde.bitcoin.transaction.script.slp;

import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.merkleroot.MutableMerkleRoot;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.opcode.Opcode;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.slp.commit.MutableSlpCommitScript;
import com.softwareverde.bitcoin.transaction.script.slp.commit.SlpCommitScript;
import com.softwareverde.bitcoin.transaction.script.slp.genesis.MutableSlpGenesisScript;
import com.softwareverde.bitcoin.transaction.script.slp.genesis.SlpGenesisScript;
import com.softwareverde.bitcoin.transaction.script.slp.mint.MutableSlpMintScript;
import com.softwareverde.bitcoin.transaction.script.slp.mint.SlpMintScript;
import com.softwareverde.bitcoin.transaction.script.slp.send.MutableSlpSendScript;
import com.softwareverde.bitcoin.transaction.script.slp.send.SlpSendScript;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.MutableSha256Hash;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;

import java.math.BigInteger;

public class SlpScriptInflater {
    protected static final Long TOKEN_TYPE_VALUE = ByteUtil.bytesToLong(SlpScriptType.TOKEN_TYPE);

    protected static Boolean _matchesSlpFormat(final LockingScript lockingScript) {
        final List<Operation> operations = lockingScript.getOperations();
        if (operations.getCount() < 5) { return false; }

        for (int i = 0; i < operations.getCount(); ++i) {
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

                if (i == 1) {
                    if (! Util.areEqual(SlpScriptType.LOKAD_ID, pushOperation.getValue())) { return false; }
                }
                else if (i == 2) {
                    final Value value = pushOperation.getValue();
                    final int valueByteCount = value.getByteCount();
                    if ( (valueByteCount < 1) || (valueByteCount > 2) ) { return false; }

                    final Long bigEndianLongValue = ByteUtil.bytesToLong(value);
                    if (! Util.areEqual(TOKEN_TYPE_VALUE, bigEndianLongValue)) { return false; }
                }
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
        if (operations.getCount() < 6) { return null; }

        final PushOperation tokenPushOperation = (PushOperation) operations.get(4);
        final ByteArray tokenIdBytes = tokenPushOperation.getValue();
        return SlpTokenId.copyOf(tokenIdBytes.getBytes());
    }

    protected static SlpGenesisScript _genesisScriptFromScript(final LockingScript lockingScript) {
        final List<Operation> operations = lockingScript.getOperations();
        if (operations.getCount() != 11) { return null; }

        final MutableSlpGenesisScript slpGenesisScript = new MutableSlpGenesisScript();

        final Value tokenAbbreviationValue = ((PushOperation) operations.get(4)).getValue();
        slpGenesisScript.setTokenAbbreviation(StringUtil.bytesToString(tokenAbbreviationValue.getBytes()));

        final Value tokenNameValue = ((PushOperation) operations.get(5)).getValue();
        slpGenesisScript.setTokenName(StringUtil.bytesToString(tokenNameValue.getBytes()));

        final Value tokenDocumentUrlValue = ((PushOperation) operations.get(6)).getValue();
        slpGenesisScript.setDocumentUrl(tokenDocumentUrlValue.getByteCount() > 0 ? StringUtil.bytesToString(tokenDocumentUrlValue.getBytes()) : null);

        final Value tokenDocumentHashValue = ((PushOperation) operations.get(7)).getValue();
        if ( (tokenDocumentHashValue.getByteCount() != 0) && (tokenDocumentHashValue.getByteCount() != Sha256Hash.BYTE_COUNT) ) { return null; }
        slpGenesisScript.setDocumentHash((! tokenDocumentHashValue.isEmpty()) ? Sha256Hash.copyOf(tokenDocumentHashValue.getBytes()) : null);

        final Value tokenDecimalValue = ((PushOperation) operations.get(8)).getValue();
        if (tokenDecimalValue.getByteCount() != 1) { return null; } // The "decimal" value must be 1 byte according to the specification.
        final int decimalCount = ByteUtil.bytesToInteger(tokenDecimalValue.getBytes());
        if ( (decimalCount < 0) || (decimalCount > 9) ) { return null; }
        slpGenesisScript.setDecimalCount(decimalCount);

        final Value batonOutputIndexValue = ((PushOperation) operations.get(9)).getValue();
        final int batonOutputByteCount = batonOutputIndexValue.getByteCount();
        if (batonOutputByteCount > 1) { return null; }
        final Integer batonOutputIndex = (batonOutputByteCount == 0 ? null : ByteUtil.bytesToInteger(batonOutputIndexValue.getBytes()));
        if (batonOutputByteCount == 1) {
            if (batonOutputIndex < 2) { return null; }
        }
        slpGenesisScript.setBatonOutputIndex(batonOutputIndex);

        final Value totalTokenCountValue = ((PushOperation) operations.get(10)).getValue();
        if (totalTokenCountValue.getByteCount() != 8) { return null; }
        slpGenesisScript.setTokenCount(ByteUtil.bytesToBigIntegerUnsigned(totalTokenCountValue.getBytes()));

        return slpGenesisScript;
    }

    protected static SlpMintScript _mintScriptFromScript(final LockingScript lockingScript) {
        final SlpTokenId tokenId = _getTokenId(lockingScript);
        if (tokenId == null) { return null; }

        final MutableSlpMintScript slpMintScript = new MutableSlpMintScript();
        slpMintScript.setTokenId(tokenId);

        final List<Operation> operations = lockingScript.getOperations();
        if (operations.getCount() != 7) { return null; }

        final Value batonOutputIndexValue = ((PushOperation) operations.get(5)).getValue();
        if (batonOutputIndexValue.getByteCount() > 1) { return null; }
        final int batonOutputIndex = ByteUtil.bytesToInteger(batonOutputIndexValue.getBytes());
        if (batonOutputIndexValue.getByteCount() == 1) {
            if (batonOutputIndex < 2) { return null; }
        }
        slpMintScript.setBatonOutputIndex(batonOutputIndex);

        final Value totalTokenCountValue = ((PushOperation) operations.get(6)).getValue();
        if (totalTokenCountValue.getByteCount() != 8) { return null; }
        slpMintScript.setTokenCount(ByteUtil.bytesToBigIntegerUnsigned(totalTokenCountValue.getBytes()));

        return slpMintScript;
    }

    protected static SlpSendScript _sendScriptFromScript(final LockingScript lockingScript) {
        final SlpTokenId tokenId = _getTokenId(lockingScript);
        if (tokenId == null) { return null; }

        final MutableSlpSendScript slpSendScript = new MutableSlpSendScript();
        slpSendScript.setTokenId(tokenId);

        final List<Operation> operations = lockingScript.getOperations();
        final int outputCount = ((operations.getCount() - 5) + 1); // +1 for the OP_RETURN output...
        if (outputCount > SlpSendScript.MAX_OUTPUT_COUNT) { return null; }

        int transactionOutputIndex = 1;
        for (int i = 5; i < operations.getCount(); ++i) {
            final PushOperation operation = (PushOperation) operations.get(i);
            final ByteArray value = operation.getValue();
            if (value.getByteCount() != 8) { return null; } // The "amount" byte count must be 8, according to the specification.
            final BigInteger amount = ByteUtil.bytesToBigIntegerUnsigned(value.getBytes());
            slpSendScript.setAmount(transactionOutputIndex, amount);
            transactionOutputIndex += 1;
        }

        return slpSendScript;
    }

    protected static SlpCommitScript _commitScriptFromScript(final LockingScript lockingScript) {
        final SlpTokenId tokenId = _getTokenId(lockingScript);
        if (tokenId == null) { return null; }

        final MutableSlpCommitScript slpCommitScript = new MutableSlpCommitScript();
        slpCommitScript.setTokenId(tokenId);

        final List<Operation> operations = lockingScript.getOperations();
        if (operations.getCount() < 9) { return null; }

        { // Block Hash...
            final PushOperation operation = (PushOperation) operations.get(5);
            final ByteArray value = operation.getValue();
            final Sha256Hash blockHash = MutableSha256Hash.wrap(value.getBytes());
            if (blockHash == null) { return null; }
            slpCommitScript.setBlockHash(blockHash);
        }

        { // Block Height...
            final PushOperation operation = (PushOperation) operations.get(6);
            final ByteArray value = operation.getValue();
            final Long blockHeight = ByteUtil.bytesToLong(value.getBytes());
            slpCommitScript.setBlockHeight(blockHeight);
        }

        { // Merkle Root...
            final PushOperation operation = (PushOperation) operations.get(7);
            final ByteArray value = operation.getValue();
            final MerkleRoot merkleRoot = MutableMerkleRoot.wrap(value.getBytes());
            if (merkleRoot == null) { return null; }
            slpCommitScript.setMerkleRoot(merkleRoot);
        }

        { // Merkle Tree Data Url...
            final PushOperation operation = (PushOperation) operations.get(8);
            final ByteArray value = operation.getValue();
            final String merkleTreeUrl = StringUtil.bytesToString(value.getBytes());
            if (merkleTreeUrl == null) { return null; }
            slpCommitScript.setMerkleTreeUrl(merkleTreeUrl);
        }

        return slpCommitScript;
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

        return _genesisScriptFromScript(lockingScript);
    }

    public SlpMintScript mintScriptFromScript(final LockingScript lockingScript) {
        if (! _matchesSlpFormat(lockingScript)) { return null; }
        final SlpScriptType slpScriptType = _getScriptType(lockingScript);
        if (! Util.areEqual(SlpScriptType.MINT, slpScriptType)) { return null; }

        return _mintScriptFromScript(lockingScript);
    }

    public SlpSendScript sendScriptFromScript(final LockingScript lockingScript) {
        if (! _matchesSlpFormat(lockingScript)) { return null; }
        final SlpScriptType slpScriptType = _getScriptType(lockingScript);
        if (! Util.areEqual(SlpScriptType.SEND, slpScriptType)) { return null; }

        return _sendScriptFromScript(lockingScript);
    }

    public SlpCommitScript commitScriptFromScript(final LockingScript lockingScript) {
        if (! _matchesSlpFormat(lockingScript)) { return null; }
        final SlpScriptType slpScriptType = _getScriptType(lockingScript);
        if (! Util.areEqual(SlpScriptType.COMMIT, slpScriptType)) { return null; }

        return _commitScriptFromScript(lockingScript);
    }

    public SlpScript fromLockingScript(final LockingScript lockingScript) {
        if (! _matchesSlpFormat(lockingScript)) { return null; }

        final SlpScriptType slpScriptType = _getScriptType(lockingScript);
        if (slpScriptType == null) { return null; }

        switch (slpScriptType) {
            case GENESIS: { return _genesisScriptFromScript(lockingScript); }
            case MINT: { return _mintScriptFromScript(lockingScript); }
            case COMMIT: { return _commitScriptFromScript(lockingScript); }
            case SEND: { return _sendScriptFromScript(lockingScript); }
        }

        return null;
    }
}
