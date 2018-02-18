package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class TransactionInput {
    public static final Integer MAX_SEQUENCE_NUMBER = 0xFFFFFFFF;

    protected byte[] _previousTransactionOutputHash = new byte[32];
    protected Integer _previousTransactionOutputIndex = 0;
    protected byte[] _signatureScript = new byte[0];
    protected Integer _sequenceNumber = MAX_SEQUENCE_NUMBER;

    public byte[] getPreviousTransactionOutput() { return ByteUtil.copyBytes(_previousTransactionOutputHash); }
    public void setPreviousTransactionOutput(final byte[] previousTransactionOutputHash) { ByteUtil.setBytes(_previousTransactionOutputHash, previousTransactionOutputHash); }

    public Integer getPreviousTransactionOutputIndex() { return _previousTransactionOutputIndex; }
    public void setPreviousTransactionOutputIndex(final Integer index) { _previousTransactionOutputIndex = index; }

    public byte[] getSignatureScript() { return ByteUtil.copyBytes(_signatureScript); }
    public void setSignatureScript(final byte[] signatureScript) { _signatureScript = ByteUtil.copyBytes(signatureScript); }

    public Integer getSequenceNumber() { return _sequenceNumber; }
    public void setSequenceNumber(final Integer sequenceNumber) { _sequenceNumber = sequenceNumber; }

    public Integer getByteCount() {
        final Integer previousTransactionOutputHashByteCount = 32;

        final Integer scriptByteCount;
        {
            Integer byteCount = 0;
            byteCount += ByteUtil.variableLengthIntegerToBytes(_signatureScript.length).length;
            byteCount += _signatureScript.length;
            scriptByteCount = byteCount;
        }

        final Integer sequenceByteCount = 4;

        return (previousTransactionOutputHashByteCount + scriptByteCount + sequenceByteCount);
    }

    public TransactionInput copy() {
        final TransactionInput transactionInput = new TransactionInput();
        ByteUtil.setBytes(transactionInput._previousTransactionOutputHash, _previousTransactionOutputHash);
        transactionInput._previousTransactionOutputIndex = _previousTransactionOutputIndex;
        transactionInput._signatureScript = ByteUtil.copyBytes(_signatureScript);
        transactionInput._sequenceNumber = _sequenceNumber;
        return transactionInput;
    }

    public byte[] getBytes() {
        final byte[] sequenceBytes = new byte[4];
        ByteUtil.setBytes(sequenceBytes, ByteUtil.integerToBytes(_sequenceNumber));

        final byte[] indexBytes = new byte[4];
        ByteUtil.setBytes(indexBytes, ByteUtil.integerToBytes(_previousTransactionOutputIndex));

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        byteArrayBuilder.appendBytes(_previousTransactionOutputHash, Endian.LITTLE); // TODO: Unsure if Big or Little endian...
        byteArrayBuilder.appendBytes(indexBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(_signatureScript.length), Endian.BIG);
        byteArrayBuilder.appendBytes(_signatureScript, Endian.LITTLE); // TODO: Unsure if Big or Little endian...
        byteArrayBuilder.appendBytes(sequenceBytes, Endian.LITTLE);

        return byteArrayBuilder.build();
    }
}
