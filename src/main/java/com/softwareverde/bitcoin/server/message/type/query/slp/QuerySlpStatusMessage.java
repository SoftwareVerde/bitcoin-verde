package com.softwareverde.bitcoin.server.message.type.query.slp;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class QuerySlpStatusMessage extends BitcoinProtocolMessage {
    public static final Integer MAX_HASH_COUNT = 1024;

    protected final MutableList<Sha256Hash> _transactionHashList = new MutableList<>();

    public QuerySlpStatusMessage() {
        super(MessageType.QUERY_SLP_STATUS);
    }

    public void addHash(final Sha256Hash slpTransactionHash) {
        if (_transactionHashList.getCount() >= MAX_HASH_COUNT) { return; }
        _transactionHashList.add(slpTransactionHash);
    }

    public List<Sha256Hash> getHashes() {
        return _transactionHashList;
    }

    @Override
    protected ByteArray _getPayload() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(_transactionHashList.getCount()));
        for (final Sha256Hash hash : _transactionHashList) {
            byteArrayBuilder.appendBytes(hash.getBytes(), Endian.LITTLE);
        }

        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}
