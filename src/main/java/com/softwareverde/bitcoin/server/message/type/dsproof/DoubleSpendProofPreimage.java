package com.softwareverde.bitcoin.server.message.type.dsproof;

import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class DoubleSpendProofPreimage {
    protected Long _transactionVersion;                   // Transaction Preimage Component #1
    protected SequenceNumber _sequenceNumber;
    protected LockTime _lockTime;
    protected Sha256Hash _previousOutputsDigest;          // Transaction Preimage Component #2
    protected Sha256Hash _sequenceNumbersDigest;          // Transaction Preimage Component #3
    protected Sha256Hash _transactionOutputsDigest;       // Transaction Preimage Component #8

    protected final MutableList<ByteArray> _unlockingScriptPushData = new MutableList<>();

    protected DoubleSpendProofPreimage() { }

    public Long getTransactionVersion() {
        return _transactionVersion;
    }

    public SequenceNumber getSequenceNumber() {
        return _sequenceNumber;
    }

    public LockTime getLockTime() {
        return _lockTime;
    }

    public Sha256Hash getPreviousOutputsDigest() {
        return _previousOutputsDigest;
    }

    public Sha256Hash getSequenceNumbersDigest() {
        return _sequenceNumbersDigest;
    }

    public Sha256Hash getTransactionOutputsDigest() {
        return _transactionOutputsDigest;
    }

    public List<ByteArray> getUnlockingScriptPushData() {
        return _unlockingScriptPushData;
    }
}
