package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class IndexedOutput {
    public final TransactionId transactionId;
    public final Integer outputIndex;
    public final Long amount;
    public final ScriptType scriptType;
    public final Address address;
    public final Sha256Hash scriptHash;
    public final TransactionId slpTransactionId;
    public final ByteArray memoActionType;
    public final ByteArray memoActionIdentifier;

    public IndexedOutput(final TransactionId transactionId, final Integer outputIndex, final Long amount, final ScriptType scriptType, final Address address, final Sha256Hash scriptHash, final TransactionId slpTransactionId, final ByteArray memoActionType, final ByteArray memoActionIdentifier) {
        this.transactionId = transactionId;
        this.outputIndex = outputIndex;
        this.amount = amount;
        this.scriptType = scriptType;
        this.address = address;
        this.scriptHash = scriptHash;
        this.slpTransactionId = slpTransactionId;
        this.memoActionType = memoActionType;
        this.memoActionIdentifier = memoActionIdentifier;
    }
}
