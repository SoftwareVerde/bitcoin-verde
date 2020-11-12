package com.softwareverde.bitcoin.server.message.type.query.slp;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class QuerySlpStatusMessage extends BitcoinProtocolMessage {
    public static final Integer MAX_HASH_COUNT = 1024;

    protected final MutableList<Sha256Hash> _transactionHashes = new MutableList<>();

    public QuerySlpStatusMessage() {
        super(MessageType.QUERY_SLP_STATUS);
    }

    public void addHash(final Sha256Hash slpTransactionHash) {
        if (_transactionHashes.getCount() >= MAX_HASH_COUNT) { return; }
        _transactionHashes.add(slpTransactionHash);
    }

    public List<Sha256Hash> getHashes() {
        return _transactionHashes;
    }

    @Override
    protected ByteArray _getPayload() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(_transactionHashes.getCount()));
        for (final Sha256Hash transactionHash : _transactionHashes) {
            byteArrayBuilder.appendBytes(transactionHash, Endian.LITTLE);
        }

        return byteArrayBuilder;
    }

    @Override
    protected Integer _getPayloadByteCount() {
        final int transactionCount = _transactionHashes.getCount();
        final byte[] transactionCountBytes = ByteUtil.variableLengthIntegerToBytes(transactionCount);
        return (transactionCountBytes.length + (transactionCount * Sha256Hash.BYTE_COUNT));
    }
}
