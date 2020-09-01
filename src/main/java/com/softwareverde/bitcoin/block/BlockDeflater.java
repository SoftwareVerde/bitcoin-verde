package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.json.Json;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class BlockDeflater {
    protected final BlockHeaderDeflater _blockHeaderDeflater = new BlockHeaderDeflater();
    protected final TransactionDeflater _transactionDeflater = new TransactionDeflater();

    public MutableByteArray toBytes(final Block block) {
        final List<Transaction> transactions = block.getTransactions();

        final int transactionCount = transactions.getCount();
        final ByteArrayBuilder byteArrayBuilder = _blockHeaderDeflater.toByteArrayBuilder(block);
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(transactionCount), Endian.BIG);

        for (int i = 0; i < transactionCount; ++i) {
            final Transaction transaction = transactions.get(i);
            final ByteArray transactionBytes = _transactionDeflater.toBytes(transaction);
            byteArrayBuilder.appendBytes(transactionBytes, Endian.BIG);
        }

        return MutableByteArray.wrap(byteArrayBuilder.build());
    }

    public Integer getByteCount(final Block block) {
        final List<Transaction> transactions = block.getTransactions();

        Integer byteCount = BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT;

        final int transactionCount = transactions.getCount();
        byteCount += ByteUtil.variableLengthIntegerToBytes(transactionCount).length;

        for (int i = 0; i < transactionCount; ++i) {
            final Transaction transaction = transactions.get(i);
            byteCount += _transactionDeflater.getByteCount(transaction);
        }

        return byteCount;
    }

    public Json toJson(final Block block) {
        final Json json = _blockHeaderDeflater.toJson(block);

        final Json transactionsJson = new Json(true);
        for (final Transaction transaction : block.getTransactions()) {
            transactionsJson.add(transaction);
        }
        json.put("transactions", transactionsJson);

        return json;
    }

    public BlockHeaderDeflater getBlockHeaderDeflater() {
        return _blockHeaderDeflater;
    }

    public TransactionDeflater getTransactionDeflater() {
        return _transactionDeflater;
    }
}
