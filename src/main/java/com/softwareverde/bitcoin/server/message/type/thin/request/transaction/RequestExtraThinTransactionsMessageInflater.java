package com.softwareverde.bitcoin.server.message.type.thin.request.transaction;

import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.cryptography.hash.sha256.MutableSha256Hash;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.Endian;

public class RequestExtraThinTransactionsMessageInflater extends BitcoinProtocolMessageInflater {
    @Override
    public RequestExtraThinTransactionsMessage fromBytes(final byte[] bytes) {
        final RequestExtraThinTransactionsMessage requestExtraThinTransactionsMessage = new RequestExtraThinTransactionsMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.REQUEST_EXTRA_THIN_TRANSACTIONS);
        if (protocolMessageHeader == null) { return null; }

        final Sha256Hash blockHash = MutableSha256Hash.wrap(byteArrayReader.readBytes(32, Endian.LITTLE));
        requestExtraThinTransactionsMessage.setBlockHash(blockHash);

        final Integer transactionCount = byteArrayReader.readVariableLengthInteger().intValue();
        if (transactionCount >= BitcoinConstants.getMaxTransactionCountPerBlock()) { return null; }

        final ImmutableListBuilder<ByteArray> transactionShortHashesListBuilder = new ImmutableListBuilder<ByteArray>(transactionCount);
        for (int i = 0; i < transactionCount; ++i) {
            final ByteArray transactionShortHash = MutableByteArray.wrap(byteArrayReader.readBytes(4, Endian.LITTLE));
            transactionShortHashesListBuilder.add(transactionShortHash);
        }
        final List<ByteArray> transactionShortHashes = transactionShortHashesListBuilder.build();
        requestExtraThinTransactionsMessage.setTransactionShortHashes(transactionShortHashes);

        if (byteArrayReader.didOverflow()) { return null; }

        return requestExtraThinTransactionsMessage;
    }
}
