package com.softwareverde.bitcoin.block.thin;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.module.node.MemoryPoolEnquirer;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

import java.util.HashMap;
import java.util.Map;

public class ThinBlockAssembler {
    protected final MemoryPoolEnquirer _memoryPoolEnquirer;

    public ThinBlockAssembler(final MemoryPoolEnquirer memoryPoolEnquirer) {
        _memoryPoolEnquirer = memoryPoolEnquirer;
    }

    public AssembleThinBlockResult assembleThinBlock(final BlockHeader blockHeader, final List<Sha256Hash> transactionHashes, final List<Transaction> extraTransactions) {
        final HashMap<Sha256Hash, Transaction> mappedTransactions = new HashMap<Sha256Hash, Transaction>();
        for (final Transaction transaction : extraTransactions) {
            final Sha256Hash transactionHash = transaction.getHash();
            mappedTransactions.put(transactionHash, transaction);
        }

        final MutableBlock mutableBlock = new MutableBlock(blockHeader);
        for (final Sha256Hash transactionHash : transactionHashes) {
            final Transaction cachedTransaction = mappedTransactions.get(transactionHash);
            if (cachedTransaction != null) {
                mutableBlock.addTransaction(cachedTransaction);
            }
            else {
                final Transaction transaction = _memoryPoolEnquirer.getTransaction(transactionHash);
                if (transaction == null) { break; }

                mappedTransactions.put(transactionHash, transaction);
                mutableBlock.addTransaction(transaction);
            }
        }

        final Block block = (mutableBlock.isValid() ? mutableBlock : null);

        final AssembleThinBlockResult assembleThinBlockResult = new AssembleThinBlockResult(block, transactionHashes);
        assembleThinBlockResult.allowReassembly(blockHeader, transactionHashes, mappedTransactions);
        return assembleThinBlockResult;
    }

    public Block reassembleThinBlock(final AssembleThinBlockResult assembleThinBlockResult, final List<Transaction> missingTransactions) {
        if (! assembleThinBlockResult.canBeReassembled()) { return null; }

        final BlockHeader blockHeader = assembleThinBlockResult.getBlockHeader();
        final List<Sha256Hash> transactionHashes = assembleThinBlockResult.getTransactionHashes();
        final Map<Sha256Hash, Transaction> mappedTransactions = assembleThinBlockResult.getMappedTransactions();

        for (final Transaction transaction : missingTransactions) {
            final Sha256Hash transactionHash = transaction.getHash();
            mappedTransactions.put(transactionHash, transaction);
        }

        final MutableBlock mutableBlock = new MutableBlock(blockHeader);
        for (final Sha256Hash transactionHash : transactionHashes) {
            final Transaction cachedTransaction = mappedTransactions.get(transactionHash);
            if (cachedTransaction != null) {
                mutableBlock.addTransaction(cachedTransaction);
            }
            else {
                final Transaction transaction = _memoryPoolEnquirer.getTransaction(transactionHash);
                if (transaction == null) { return null; }
                mutableBlock.addTransaction(transaction);
            }
        }

        return (mutableBlock.isValid() ? mutableBlock : null);
    }
}
