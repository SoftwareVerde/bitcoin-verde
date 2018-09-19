package com.softwareverde.bitcoin.block.thin;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;

import java.util.Map;

public class AssembleThinBlockResult {
    private BlockHeader _blockHeader;
    private List<Sha256Hash> _transactionHashes;
    private Map<Sha256Hash, Transaction> _mappedTransactions;

    protected AssembleThinBlockResult(final Block block, final List<Sha256Hash> missingTransactions) {
        this.block = block;
        this.missingTransactions = missingTransactions.asConst();
    }

    protected void allowReassembly(final BlockHeader blockHeader, final List<Sha256Hash> transactionHashes, final Map<Sha256Hash, Transaction> mappedTransactions) {
        _blockHeader = blockHeader;
        _transactionHashes = transactionHashes;
        _mappedTransactions = mappedTransactions;
    }

    protected BlockHeader getBlockHeader() { return _blockHeader; }
    protected List<Sha256Hash> getTransactionHashes() { return _transactionHashes; }
    protected Map<Sha256Hash, Transaction> getMappedTransactions() { return _mappedTransactions; }

    public final Block block;
    public final List<Sha256Hash> missingTransactions;

    public Boolean wasSuccessful() {
        return (this.block != null);
    }

    public Boolean canBeReassembled() {
        return ( (_blockHeader != null) && (_transactionHashes != null) && (_mappedTransactions != null) );
    }
}