package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.map.Map;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;

public class BlockOutputs {
    protected final Sha256Hash _coinbaseTransactionHash;
    protected final Map<TransactionOutputIdentifier, TransactionOutput> _transactionOutputs;

    public static BlockOutputs fromBlock(final Block block) {
        final List<Transaction> transactions = block.getTransactions();
        final MutableHashMap<TransactionOutputIdentifier, TransactionOutput> transactionOutputMap = new MutableHashMap<>(transactions.getCount());

        final Transaction coinbaseTransaction = block.getCoinbaseTransaction();
        final Sha256Hash coinbaseTransactionHash = coinbaseTransaction.getHash();

        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();
            final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
            int outputIndex = 0;
            for (final TransactionOutput transactionOutput : transactionOutputs) {
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
                transactionOutputMap.put(transactionOutputIdentifier, transactionOutput);
                outputIndex += 1;
            }
        }

        return new BlockOutputs(coinbaseTransactionHash, transactionOutputMap);
    }

    protected BlockOutputs(final Sha256Hash coinbaseTransactionHash, final Map<TransactionOutputIdentifier, TransactionOutput> transactionOutputs) {
        _coinbaseTransactionHash = coinbaseTransactionHash;
        _transactionOutputs = transactionOutputs;
    }

    public BlockOutputs() {
        _coinbaseTransactionHash = null;
        _transactionOutputs = new MutableHashMap<>(0);
    }

    public TransactionOutput getTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return _transactionOutputs.get(transactionOutputIdentifier);
    }

    public Boolean isCoinbaseTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        final boolean transactionIsWithinThisBlock = _transactionOutputs.containsKey(transactionOutputIdentifier);
        if (! transactionIsWithinThisBlock) { return null; }

        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
        return Util.areEqual(_coinbaseTransactionHash, transactionHash);

    }

    public Integer getOutputCount() {
        return _transactionOutputs.getCount();
    }
}
