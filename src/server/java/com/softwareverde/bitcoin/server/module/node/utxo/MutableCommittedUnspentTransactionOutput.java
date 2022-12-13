package com.softwareverde.bitcoin.server.module.node.utxo;

import com.softwareverde.bitcoin.transaction.output.MutableUnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputDeflater;
import com.softwareverde.bitcoin.transaction.output.UnspentTransactionOutput;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class MutableCommittedUnspentTransactionOutput extends MutableUnspentTransactionOutput implements CommittedUnspentTransactionOutput {
    protected Sha256Hash _transactionHash;

    public MutableCommittedUnspentTransactionOutput() { }

    public MutableCommittedUnspentTransactionOutput(final Sha256Hash transactionHash, final UnspentTransactionOutput unspentTransactionOutput) {
        super(unspentTransactionOutput);
        _transactionHash = transactionHash;
    }

    public MutableCommittedUnspentTransactionOutput(final CommittedUnspentTransactionOutput unspentTransactionOutput) {
        super(unspentTransactionOutput);
        _transactionHash = unspentTransactionOutput.getTransactionHash();
        _isCoinbase = unspentTransactionOutput.isCoinbase();
    }

    public void setTransactionHash(final Sha256Hash transactionHash) {
        _transactionHash = transactionHash;
    }

    @Override
    public Sha256Hash getTransactionHash() {
        return _transactionHash;
    }

    @Override
    public Integer getByteCount() {
        return (CommittedUnspentTransactionOutputInflater.NON_LOCKING_SCRIPT_BYTE_COUNT + _lockingScript.getByteCount());
    }

    @Override
    public ByteArray getBytes() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        final MutableByteArray blockHeightAndIsCoinbaseBytes;
        {
            final long shiftedBlockHeight = _blockHeight << CommittedUnspentTransactionOutput.BLOCK_HEIGHT_BIT_SHIFT_COUNT;
            blockHeightAndIsCoinbaseBytes = MutableByteArray.wrap(ByteUtil.integerToBytes(shiftedBlockHeight));
            if (_isCoinbase) {
                blockHeightAndIsCoinbaseBytes.setBit(CommittedUnspentTransactionOutput.IS_COINBASE_FLAG_BIT_INDEX, true);
            }
        }

        byteArrayBuilder.appendBytes(_transactionHash, Endian.LITTLE); // 32 bytes
        byteArrayBuilder.appendBytes(CompactVariableLengthInteger.variableLengthIntegerToBytes(_index)); // 1-4 bytes
        byteArrayBuilder.appendBytes(blockHeightAndIsCoinbaseBytes, Endian.LITTLE); // 4 bytes
        byteArrayBuilder.appendBytes(CompactVariableLengthInteger.variableLengthIntegerToBytes(_amount)); // 1-8 bytes

        // NOTE: Due to ambiguity in the original specification, BCHD defined the LockingScript byte count as a 4-byte integer instead of a variable-length integer.
        //  Verde's implementation uses the compact variable-length integer format, which is incompatible with BCHD's previous format.
        byteArrayBuilder.appendBytes(CompactVariableLengthInteger.variableLengthIntegerToBytes(_lockingScript.getByteCount())); // 1-4 bytes

        final TransactionOutputDeflater transactionOutputDeflater = new TransactionOutputDeflater();
        final ByteArray lockingScriptBytes = transactionOutputDeflater.toLegacyScriptBytes(this);
        byteArrayBuilder.appendBytes(lockingScriptBytes); // ? bytes

        return byteArrayBuilder;
    }
}
