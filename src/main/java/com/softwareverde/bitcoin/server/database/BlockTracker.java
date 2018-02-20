package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockTracker {
    private static class BlockTrackerInstance {
        static final BlockTracker instance = new BlockTracker();
    }

    public static synchronized BlockTracker getInstance() {
        return BlockTrackerInstance.instance;
    }

    public static class TransactionOutputId {
        protected final Hash _transactionHash;
        protected final Integer _outputIndex;

        public TransactionOutputId(final Hash transactionHash, final Integer outputIndex) {
            _transactionHash = new ImmutableHash(transactionHash);
            _outputIndex = outputIndex;
        }

        public Hash getTransactionHash() {
            return _transactionHash;
        }

        public Integer getOutputIndex() {
            return _outputIndex;
        }

        @Override
        public int hashCode() {
            long sum = _outputIndex;
            for (byte b : _transactionHash.getBytes()) {
                sum += ByteUtil.byteToLong(b);
            }
            return Long.valueOf(sum).hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == null) { return false; }
            if (! (obj instanceof TransactionOutputId)) { return false; }

            final TransactionOutputId object = (TransactionOutputId) obj;
            if (! ByteUtil.areEqual(_transactionHash.getBytes(), object._transactionHash.getBytes())) { return false; }
            if (! _outputIndex.equals(object._outputIndex)) { return false; }
            return true;
        }
    }

    protected Block _genesisBlock = null;
    protected final Map<Hash, Block> _blocks = new HashMap<Hash, Block>();
    protected final Map<TransactionOutputId, TransactionOutput> _unspentTransactionOutputs = new HashMap<TransactionOutputId, TransactionOutput>();

    protected BlockTracker() { }

    protected TransactionOutput _findTransactionOutput(final TransactionOutputId transactionOutputId) {
        return _unspentTransactionOutputs.get(transactionOutputId);
    }

    protected void _processTransactions(final Block block) {
        final List<Transaction> transactions = block.getTransactions();
        for (final Transaction transaction : transactions) {
            final Hash transactionHash = transaction.calculateSha256Hash();
            for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
                final Integer outputIndex = transactionOutput.getIndex();
                _unspentTransactionOutputs.put(new TransactionOutputId(transactionHash, outputIndex), transactionOutput);
                System.out.println("\t TX: "+ BitcoinUtil.toHexString(transactionHash) +":"+ outputIndex + " = "+ transactionOutput.getAmount());
            }
        }
    }

    protected Long _calculateTotalTransactionInputs(final Transaction blockTransaction) {
        long totalInputValue = 0L;
        final List<TransactionInput> transactionInputs = blockTransaction.getTransactionInputs();
        for (final TransactionInput transactionInput : transactionInputs) {
            final Hash transactionInputOutputHash = transactionInput.getPreviousTransactionOutput();
            final Integer transactionInputOutputIndex = transactionInput.getPreviousTransactionOutputIndex();
            final TransactionOutput transactionOutput = _findTransactionOutput(new TransactionOutputId(transactionInputOutputHash, transactionInputOutputIndex));
            if (transactionOutput == null) {
                System.out.println("Couldn't Find: "+ BitcoinUtil.toHexString(transactionInputOutputHash) +":"+ transactionInputOutputIndex);
                return null;
            }

            totalInputValue += transactionOutput.getAmount();
        }
        return totalInputValue;
    }

    protected Boolean _validateTransactionExpenditure(final Transaction blockTransaction) {
        final Long totalOutputValue = blockTransaction.getTotalOutputValue();
        final Long totalInputValue = _calculateTotalTransactionInputs(blockTransaction);
        System.out.println("(Inputs: "+ totalInputValue+"; Outputs: "+ totalOutputValue +")");
        if (totalInputValue == null) { return false; }

        return (totalOutputValue <= totalInputValue);
    }

    public void addBlock(final Block block) {
        if (_genesisBlock == null) {
            _genesisBlock = block;
        }

        _blocks.put(block.calculateSha256Hash(), block);
        _processTransactions(block);
    }

    public Boolean validateBlock(final Block block) {
        if (_genesisBlock == null) { return true; }

        if (! block.validateBlockHeader()) { return false; }

        final List<Transaction> blockTransactions = block.getTransactions();
        for (int i=0; i<blockTransactions.size(); ++i) {
            if (i == 0) { continue; } // TODO: The coinbase transaction requires a separate validation process...

            final Transaction blockTransaction = blockTransactions.get(i);
            final Boolean transactionExpenditureIsValid = _validateTransactionExpenditure(blockTransaction);
            if (! transactionExpenditureIsValid) { return false; }
        }

        return true;
    }

    public Integer getBlockCount() {
        return _blocks.size();
    }
}
