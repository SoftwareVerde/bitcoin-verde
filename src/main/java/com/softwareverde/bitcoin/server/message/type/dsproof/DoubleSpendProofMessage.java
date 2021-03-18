package com.softwareverde.bitcoin.server.message.type.dsproof;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProof;
import com.softwareverde.constable.bytearray.ByteArray;

public class DoubleSpendProofMessage extends BitcoinProtocolMessage {
    protected DoubleSpendProof _doubleSpendProof;

    public DoubleSpendProofMessage() {
        super(MessageType.DOUBLE_SPEND_PROOF);
    }

    public DoubleSpendProofMessage(final DoubleSpendProof doubleSpendProof) {
        super(MessageType.DOUBLE_SPEND_PROOF);
        _doubleSpendProof = doubleSpendProof;
    }

    public DoubleSpendProof getDoubleSpendProof() {
        return _doubleSpendProof;
    }

    public void setDoubleSpendProof(final DoubleSpendProof doubleSpendProof) {
        _doubleSpendProof = doubleSpendProof;
    }

    @Override
    protected ByteArray _getPayload() {
        if (_doubleSpendProof == null) { return null; }
        return _doubleSpendProof.getBytes();
    }

    @Override
    protected Integer _getPayloadByteCount() {
        final ByteArray payload = _getPayload();
        if (payload == null) { return 0; }

        return payload.getByteCount();
    }
}
