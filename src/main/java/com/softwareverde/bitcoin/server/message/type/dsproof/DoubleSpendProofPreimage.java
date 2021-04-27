package com.softwareverde.bitcoin.server.message.type.dsproof;

import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProof;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.Mode;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

import java.util.HashMap;

public class DoubleSpendProofPreimage {
    protected Long _transactionVersion;                     // Transaction Preimage Component #1
    protected SequenceNumber _sequenceNumber;               // Transaction Preimage Component #9
    protected LockTime _lockTime;                           // Transaction Preimage Component #11
    protected Sha256Hash _previousOutputsDigest;            // Transaction Preimage Component #2
    protected Sha256Hash _sequenceNumbersDigest;            // Transaction Preimage Component #3
    protected Sha256Hash _executedTransactionOutputsDigest; // Transaction Preimage Component #8
    protected HashMap<Mode, Sha256Hash> _alternateTransactionOutputsDigests = new HashMap<>(); // Alternate Preimage Component #8

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

    public Sha256Hash getExecutedTransactionOutputsDigest() {
        return _executedTransactionOutputsDigest;
    }

    public List<HashType> getExtraTransactionOutputsDigestHashTypes() {
        final MutableList<HashType> hashTypes = new MutableList<>();
        for (final HashType hashType : DoubleSpendProof.SUPPORTED_HASH_TYPES) {
            if (_alternateTransactionOutputsDigests.containsKey(hashType.getMode())) {
                hashTypes.add(hashType);
            }
        }
        return hashTypes;
    }

    public Sha256Hash getTransactionOutputsDigest(final HashType hashType) {
        final Mode hashTypeMode = hashType.getMode();
        if (hashTypeMode == Mode.SIGNATURE_HASH_NONE) {
            return Sha256Hash.EMPTY_HASH;
        }

        final Sha256Hash alternateDigest = _alternateTransactionOutputsDigests.get(hashTypeMode);
        if (alternateDigest != null) {
            return alternateDigest;
        }

        return _executedTransactionOutputsDigest;
    }

    public List<ByteArray> getUnlockingScriptPushData() {
        return _unlockingScriptPushData;
    }

    public Boolean usesExtendedFormat() {
        return _alternateTransactionOutputsDigests.size() > 0;
    }
}
