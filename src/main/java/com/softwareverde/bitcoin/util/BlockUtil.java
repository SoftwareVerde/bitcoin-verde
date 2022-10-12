package com.softwareverde.bitcoin.util;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockUtxoDiff;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class BlockUtil {
    protected BlockUtil() { }

    public static MutableList<TransactionOutputIdentifier> getRequiredPreviousOutputIdentifiers(final Block block) {
        final MutableList<TransactionOutputIdentifier> transactionOutputIdentifiers = new MutableList<>();

        boolean isCoinbase = true;
        for (final Transaction transaction : block.getTransactions()) {
            if (isCoinbase) {
                isCoinbase = false;
                continue;
            }

            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                transactionOutputIdentifiers.add(transactionOutputIdentifier);
            }
        }

        return transactionOutputIdentifiers;
    }

    public static BlockUtxoDiff getBlockUtxoDiff(final Block block) {
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();
        final BlockUtxoDiff blockUtxoDiff = new BlockUtxoDiff();

        final List<Transaction> transactions = block.getTransactions();
        blockUtxoDiff.transactionCount = transactions.getCount();

        {
            final Transaction coinbaseTransaction = transactions.get(0);
            blockUtxoDiff.coinbaseTransactionHash = coinbaseTransaction.getHash();
        }

        for (int i = 0; i < transactions.getCount(); ++i) {
            final Transaction transaction = transactions.get(i);
            final Sha256Hash transactionHash = transaction.getHash();
            final Sha256Hash constTransactionHash = transactionHash.asConst();

            final boolean isCoinbase = (i == 0);
            if (! isCoinbase) {
                for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                    blockUtxoDiff.spentTransactionOutputIdentifiers.add(transactionOutputIdentifier);
                }
            }

            int outputIndex = 0;
            for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
                final LockingScript lockingScript = transactionOutput.getLockingScript();
                final boolean isPossiblySpendable = (! scriptPatternMatcher.isProvablyUnspendable(lockingScript));

                if (isPossiblySpendable) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(constTransactionHash, outputIndex);
                    blockUtxoDiff.unspentTransactionOutputIdentifiers.add(transactionOutputIdentifier);
                    blockUtxoDiff.unspentTransactionOutputs.add(transactionOutput);
                }
                else {
                    blockUtxoDiff.unspendableCount += 1;
                }

                outputIndex += 1;
            }
        }

        return blockUtxoDiff;
    }
}
