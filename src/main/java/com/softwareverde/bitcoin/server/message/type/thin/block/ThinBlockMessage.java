package com.softwareverde.bitcoin.server.message.type.thin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class ThinBlockMessage extends BitcoinProtocolMessage {

    protected BlockHeader _blockHeader;
    protected List<Sha256Hash> _transactionHashes = new MutableList<Sha256Hash>(0);
    protected List<Transaction> _missingTransactions = new MutableList<Transaction>(0);

    public ThinBlockMessage() {
        super(MessageType.THIN_BLOCK);
    }

    public BlockHeader getBlockHeader() {
        return _blockHeader;
    }

    public List<Sha256Hash> getTransactionHashes() {
        return _transactionHashes;
    }

    public List<Transaction> getMissingTransactions() {
        return _missingTransactions;
    }

    public void setBlockHeader(final BlockHeader blockHeader) {
        _blockHeader = blockHeader;
    }

    public void setTransactionHashes(final List<Sha256Hash> transactionHashes) {
        _transactionHashes = transactionHashes.asConst();
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
            final int transactionCount = _transactionHashes.getCount();
            byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(transactionCount));
            for (final Sha256Hash transactionHash : _transactionHashes) {
                byteArrayBuilder.appendBytes(transactionHash, Endian.LITTLE);
            }
        }

        { // Known Missing Transactions...
            final int missingTransactionCount = _missingTransactions.getCount();
            byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(missingTransactionCount));
            for (final Transaction transaction : _missingTransactions) {
                byteArrayBuilder.appendBytes(transactionDeflater.toBytes(transaction));
            }
        }

        return byteArrayBuilder;
    }

    @Override
    protected Integer _getPayloadByteCount() {
        final int transactionCount = _transactionHashes.getCount();
        final byte[] transactionCountBytes = ByteUtil.variableLengthIntegerToBytes(transactionCount);

        final int missingTransactionCount = _missingTransactions.getCount();
        final byte[] missingTransactionCountBytes = ByteUtil.variableLengthIntegerToBytes(missingTransactionCount);

        return (
            BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT +
            transactionCountBytes.length +
            (transactionCount * Sha256Hash.BYTE_COUNT) +
            missingTransactionCountBytes.length +
            (missingTransactionCount * Sha256Hash.BYTE_COUNT)
        );
    }
}
