package com.softwareverde.bitcoin.block.thin;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.map.Map;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class AssembleThinBlockResult {
    protected BlockHeader _blockHeader;
    protected List<Sha256Hash> _transactionHashes;
    protected Map<Sha256Hash, Transaction> _mappedTransactions;

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
