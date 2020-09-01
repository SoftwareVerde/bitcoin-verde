package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public interface UnspentTransactionOutputContext {

    /**
     * Two sets of coinbase transactions share duplicate hashes.  Before Bip34 it was trivially possible to create
     *  transactions with duplicate hashes via mining coinbases with the same output address.  Duplicate transactions
     *  are now near-impossible to create.  Consensus behavior dictates that historic duplicate transactions overwrite
     *  one another, so only the latter transactions is actually spendable.
     */
    List<Sha256Hash> ALLOWED_DUPLICATE_TRANSACTION_HASHES = new ImmutableList<Sha256Hash>(
        Sha256Hash.fromHexString("E3BF3D07D4B0375638D5F1DB5255FE07BA2C4CB067CD81B84EE974B6585FB468"),
        Sha256Hash.fromHexString("D5D27987D2A3DFC724E359870C6644B40E497BDC0589A033220FE15429D88599")
    );

    TransactionOutput getTransactionOutput(TransactionOutputIdentifier transactionOutputIdentifier);
    Long getBlockHeight(TransactionOutputIdentifier transactionOutputIdentifier);
    Sha256Hash getBlockHash(TransactionOutputIdentifier transactionOutputIdentifier);
    Boolean isCoinbaseTransactionOutput(TransactionOutputIdentifier transactionOutputIdentifier);
}
