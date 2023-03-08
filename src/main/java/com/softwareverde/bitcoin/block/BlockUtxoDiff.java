package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class BlockUtxoDiff {
    public final MutableList<TransactionOutputIdentifier> spentTransactionOutputIdentifiers = new MutableList<>();
    public final MutableList<TransactionOutputIdentifier> unspentTransactionOutputIdentifiers = new MutableList<>();
    public final MutableList<TransactionOutput> unspentTransactionOutputs = new MutableList<>();
    public Sha256Hash coinbaseTransactionHash;
    public int unspendableCount = 0;
    public int transactionCount = 0;
}
