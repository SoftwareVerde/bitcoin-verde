package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class BlockDeflater {
    public ByteArray toBytes(final Block block) {
        final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
        final TransactionDeflater transactionDeflater = new TransactionDeflater();

        final List<Transaction> transactions = block.getTransactions();

        final int transactionCount = transactions.getSize();
        final ByteArrayBuilder byteArrayBuilder = blockHeaderDeflater.toByteArrayBuilder(block);
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(transactionCount), Endian.BIG);

        for (int i=0; i<transactionCount; ++i) {
            final Transaction transaction = transactions.get(i);
            final byte[] transactionBytes = transactionDeflater.toBytes(transaction);
            byteArrayBuilder.appendBytes(transactionBytes, Endian.BIG);
        }

        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}
