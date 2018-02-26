package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.transaction.script.MutableScript;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.type.hash.MutableHash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class MutableTransactionInput implements TransactionInput {

    protected Hash _previousTransactionOutputHash;
    protected Integer _previousTransactionOutputIndex = 0;
    protected Script _unlockingScript = null;
    protected Long _sequenceNumber = MAX_SEQUENCE_NUMBER;

    public MutableTransactionInput() {
        _previousTransactionOutputHash = new MutableHash();
    }
    public MutableTransactionInput(final TransactionInput transactionInput) {
        _previousTransactionOutputIndex = transactionInput.getPreviousTransactionOutputIndex();
        _sequenceNumber = transactionInput.getSequenceNumber();

        if (transactionInput instanceof MutableTransactionInput) {
            _previousTransactionOutputHash = new ImmutableHash(transactionInput.getPreviousTransactionOutputHash());
            _unlockingScript = transactionInput.getUnlockingScript();
        }
        else {
            _previousTransactionOutputHash = transactionInput.getPreviousTransactionOutputHash();
            _unlockingScript = transactionInput.getUnlockingScript();
        }
    }

    @Override
    public Hash getPreviousTransactionOutputHash() { return _previousTransactionOutputHash; }
    public void setPreviousTransactionOutputHash(final Hash previousTransactionOutputHash) {
        _previousTransactionOutputHash = previousTransactionOutputHash;
    }

    @Override
    public Integer getPreviousTransactionOutputIndex() { return _previousTransactionOutputIndex; }
    public void setPreviousTransactionOutputIndex(final Integer index) {
        _previousTransactionOutputIndex = index;
    }

    @Override
    public Script getUnlockingScript() { return _unlockingScript; }
    public void setUnlockingScript(final Script signatureScript) {
        _unlockingScript = signatureScript;
    }
    public void setUnlockingScript(final byte[] scriptBytes) {
        _unlockingScript = new MutableScript(scriptBytes);
    }

    @Override
    public Long getSequenceNumber() { return _sequenceNumber; }
    public void setSequenceNumber(final Long sequenceNumber) {
        _sequenceNumber = sequenceNumber;
    }

    @Override
    public Integer getByteCount() {
        final Integer previousTransactionOutputHashByteCount = 32;

        final Integer scriptByteCount;
        {
            Integer byteCount = 0;
            byteCount += ByteUtil.variableLengthIntegerToBytes(_unlockingScript.getByteCount()).length;
            byteCount += _unlockingScript.getByteCount();
            scriptByteCount = byteCount;
        }

        final Integer sequenceByteCount = 4;

        return (previousTransactionOutputHashByteCount + scriptByteCount + sequenceByteCount);
    }

    @Override
    public byte[] getBytes() {
        final byte[] sequenceBytes = new byte[4];
        ByteUtil.setBytes(sequenceBytes, ByteUtil.integerToBytes(_sequenceNumber.intValue()));

        final byte[] indexBytes = new byte[4];
        ByteUtil.setBytes(indexBytes, ByteUtil.integerToBytes(_previousTransactionOutputIndex));

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        final byte[] unlockingScriptBytes = _unlockingScript.getBytes();

        byteArrayBuilder.appendBytes(_previousTransactionOutputHash.getBytes(), Endian.LITTLE);
        byteArrayBuilder.appendBytes(indexBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(unlockingScriptBytes.length), Endian.BIG);
        byteArrayBuilder.appendBytes(unlockingScriptBytes, Endian.BIG);
        byteArrayBuilder.appendBytes(sequenceBytes, Endian.LITTLE);

        return byteArrayBuilder.build();
    }
}
