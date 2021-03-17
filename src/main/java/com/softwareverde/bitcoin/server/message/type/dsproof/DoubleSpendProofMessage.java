package com.softwareverde.bitcoin.server.message.type.dsproof;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class DoubleSpendProofMessage extends BitcoinProtocolMessage {
    protected TransactionOutputIdentifier _previousTransactionOutputIdentifier;
    protected DoubleSpendProofPreimage _doubleSpendProofPreimage0;
    protected DoubleSpendProofPreimage _doubleSpendProofPreimage1;

    public DoubleSpendProofMessage() {
        super(MessageType.DOUBLE_SPEND_PROOF);
    }

    public TransactionOutputIdentifier getPreviousTransactionOutputIdentifier() {
        return _previousTransactionOutputIdentifier;
    }

    public void setPreviousTransactionOutputIdentifier(final TransactionOutputIdentifier transactionOutputIdentifier) {
        _previousTransactionOutputIdentifier = transactionOutputIdentifier;
    }

    public void setDoubleSpendProofDigests(final DoubleSpendProofPreimage doubleSpendProofPreimage0, final DoubleSpendProofPreimage doubleSpendProofPreimage1) {
        _doubleSpendProofPreimage0 = doubleSpendProofPreimage0;
        _doubleSpendProofPreimage1 = doubleSpendProofPreimage1;
    }

    @Override
    protected ByteArray _getPayload() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        { // Serialize the previous output identifier...
            final Sha256Hash previousTransactionOutputHash = _previousTransactionOutputIdentifier.getTransactionHash();
            final Integer previousTransactionOutputIndex = _previousTransactionOutputIdentifier.getOutputIndex();
            byteArrayBuilder.appendBytes(previousTransactionOutputHash, Endian.LITTLE);
            byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(previousTransactionOutputIndex), Endian.LITTLE);
        }

        { // Serialize the double-spend-proofs...
            final DoubleSpendProofPreimageDeflater doubleSpendProofPreimageDeflater = new DoubleSpendProofPreimageDeflater();
            final ByteArray doubleSpendProofDigestBytes0 = doubleSpendProofPreimageDeflater.toBytes(_doubleSpendProofPreimage0);
            final ByteArray doubleSpendProofDigestBytes1 = doubleSpendProofPreimageDeflater.toBytes(_doubleSpendProofPreimage1);
            byteArrayBuilder.appendBytes(doubleSpendProofDigestBytes0, Endian.BIG);
            byteArrayBuilder.appendBytes(doubleSpendProofDigestBytes1, Endian.BIG);
        }

        return byteArrayBuilder;
    }

    @Override
    protected Integer _getPayloadByteCount() {
        final ByteArray payload = _getPayload();
        return payload.getByteCount();
    }
}
