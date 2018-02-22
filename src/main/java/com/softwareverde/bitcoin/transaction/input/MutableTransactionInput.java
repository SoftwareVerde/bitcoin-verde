package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.type.hash.MutableHash;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class MutableTransactionInput implements TransactionInput {

    protected Hash _previousTransactionOutputHash;
    protected Integer _previousTransactionOutputIndex = 0;
    protected byte[] _unlockingScript = new byte[0];
    protected Long _sequenceNumber = MAX_SEQUENCE_NUMBER;

    public MutableTransactionInput() {
        _previousTransactionOutputHash = new MutableHash();
    }
    public MutableTransactionInput(final TransactionInput transactionInput) {
        _previousTransactionOutputIndex = transactionInput.getPreviousTransactionOutputIndex();
        _sequenceNumber = transactionInput.getSequenceNumber();

        if (transactionInput instanceof MutableTransactionInput) {
            _previousTransactionOutputHash = new ImmutableHash(transactionInput.getPreviousTransactionOutput());
            _unlockingScript = ByteUtil.copyBytes(transactionInput.getUnlockingScript());
        }
        else {
            _previousTransactionOutputHash = transactionInput.getPreviousTransactionOutput();
            _unlockingScript = transactionInput.getUnlockingScript();
        }
    }

    @Override
    public Hash getPreviousTransactionOutput() { return _previousTransactionOutputHash; }
    public void setPreviousTransactionOutput(final Hash previousTransactionOutputHash) {
        _previousTransactionOutputHash = previousTransactionOutputHash;
    }

    @Override
    public Integer getPreviousTransactionOutputIndex() { return _previousTransactionOutputIndex; }
    public void setPreviousTransactionOutputIndex(final Integer index) {
        _previousTransactionOutputIndex = index;
    }

    @Override
    public byte[] getUnlockingScript() { return _unlockingScript; }
    public void setUnlockingScript(final byte[] signatureScript) {
        _unlockingScript = signatureScript;
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
            byteCount += ByteUtil.variableLengthIntegerToBytes(_unlockingScript.length).length;
            byteCount += _unlockingScript.length;
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

        byteArrayBuilder.appendBytes(_previousTransactionOutputHash.getBytes(), Endian.LITTLE);
        byteArrayBuilder.appendBytes(indexBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(_unlockingScript.length), Endian.BIG);
        byteArrayBuilder.appendBytes(_unlockingScript, Endian.LITTLE); // TODO: Unsure if Big or Little endian...
        byteArrayBuilder.appendBytes(sequenceBytes, Endian.LITTLE);

        return byteArrayBuilder.build();
    }
}
