package com.softwareverde.bitcoin.server.message.type.thin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class ExtraThinBlockMessage extends BitcoinProtocolMessage {

    protected BlockHeader _blockHeader;
    protected List<ByteArray> _transactionShortHashes = new MutableList<ByteArray>(0);
    protected List<Transaction> _missingTransactions = new MutableList<Transaction>(0);

    public ExtraThinBlockMessage() {
        super(MessageType.EXTRA_THIN_BLOCK);
    }

    public BlockHeader getBlockHeader() {
        return _blockHeader;
    }

    public List<ByteArray> getTransactionShortHashes() {
        return _transactionShortHashes;
    }

    public List<Transaction> getMissingTransactions() {
        return _missingTransactions;
    }

    public void setBlockHeader(final BlockHeader blockHeader) {
        _blockHeader = blockHeader;
    }

    public void setTransactionHashes(final List<ByteArray> shortTransactionHashes) {
        _transactionShortHashes = shortTransactionHashes.asConst();
    }

    public void setMissingTransactions(final List<Transaction> missingTransactions) {
        _missingTransactions = missingTransactions.asConst();
    }

    @Override
    protected ByteArray _getPayload() {
        final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
        final TransactionDeflater transactionDeflater = new TransactionDeflater();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        { // Block Header...
            byteArrayBuilder.appendBytes(blockHeaderDeflater.toBytes(_blockHeader));
        }

        { // Transaction (Short) Hashes...
            final Integer transactionCount = _transactionShortHashes.getSize();
            byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(transactionCount));
            for (final ByteArray byteArray : _transactionShortHashes) {
                byteArrayBuilder.appendBytes(byteArray, Endian.LITTLE);
            }
        }

        { // Known Missing Transactions...
            final Integer missingTransactionCount = _missingTransactions.getSize();
            byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(missingTransactionCount));
            for (final Transaction transaction : _missingTransactions) {
                byteArrayBuilder.appendBytes(transactionDeflater.toBytes(transaction));
            }
        }

        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}
