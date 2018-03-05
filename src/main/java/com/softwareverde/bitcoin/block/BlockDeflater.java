package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.constable.list.List;

public class BlockDeflater {
    public byte[] toBytes(final Block block) {
        final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
        final TransactionDeflater transactionDeflater = new TransactionDeflater();

        final List<? extends Transaction> transactions = block.getTransactions();

        final byte[] headerBytes = blockHeaderDeflater.toBytes(block);
        final int transactionCount = transactions.getSize();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(headerBytes, Endian.BIG);
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(transactionCount), Endian.LITTLE);

        for (int i=0; i<transactionCount; ++i) {
            final Transaction transaction = transactions.get(i);
            final byte[] transactionBytes = transactionDeflater.toBytes(transaction);
            byteArrayBuilder.appendBytes(transactionBytes, Endian.BIG);
        }

        return byteArrayBuilder.build();
    }
}
