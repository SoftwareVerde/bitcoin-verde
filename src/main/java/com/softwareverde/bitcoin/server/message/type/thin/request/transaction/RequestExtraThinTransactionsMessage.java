package com.softwareverde.bitcoin.server.message.type.thin.request.transaction;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class RequestExtraThinTransactionsMessage extends BitcoinProtocolMessage {
    protected Sha256Hash _blockHash = Sha256Hash.EMPTY_HASH;
    protected List<ByteArray> _transactionShortHashes = new MutableList<>(0);

    public RequestExtraThinTransactionsMessage() {
        super(MessageType.REQUEST_EXTRA_THIN_TRANSACTIONS);
    }

    public void setBlockHash(final Sha256Hash blockHash) {
        _blockHash = blockHash;
    }

    public Sha256Hash getBlockHash() {
        return _blockHash;
    }

    public void setTransactionShortHashes(final List<ByteArray> transactionShortHashes) {
        _transactionShortHashes = transactionShortHashes.asConst();
    }

    public List<ByteArray> getTransactionShortHashes() {
        return _transactionShortHashes;
    }

    @Override
    protected ByteArray _getPayload() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(_blockHash, Endian.LITTLE);

        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(_transactionShortHashes.getCount()));
        for (final ByteArray transactionShortHash : _transactionShortHashes) {
            byteArrayBuilder.appendBytes(transactionShortHash, Endian.LITTLE);
        }

        return byteArrayBuilder;
    }

    @Override
    protected Integer _getPayloadByteCount() {
        return null;
    }
}
