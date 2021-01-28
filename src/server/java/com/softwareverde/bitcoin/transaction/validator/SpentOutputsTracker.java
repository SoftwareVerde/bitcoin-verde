package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.util.Util;

import java.util.concurrent.ConcurrentHashMap;

public class SpentOutputsTracker {
    protected final ConcurrentHashMap<TransactionOutputIdentifier, Boolean> _spentOutputs;

    public SpentOutputsTracker() {
        _spentOutputs = new ConcurrentHashMap<TransactionOutputIdentifier, Boolean>(256, 0.75F, 4);
    }

    /**
     * Instantiates a tracker to be used for determining if an output has been spent multiple times within the same block.
     *  This tracker is thread-safe.
     *  `threadCount` may be used to optimize concurrent threads read/writes.
     */
    public SpentOutputsTracker(final Integer estimatedOutputCount, final Integer threadCount) {
        _spentOutputs = new ConcurrentHashMap<TransactionOutputIdentifier, Boolean>(estimatedOutputCount, 0.75F, threadCount);
    }

    /**
     * Marks the TransactionOutputIdentifier as spent.
     *  Returns true iff the output has been spent already, otherwise returns false.
     */
    public Boolean markOutputAsSpent(final TransactionOutputIdentifier transactionOutputIdentifier) {
        final Boolean wasSpent = _spentOutputs.put(transactionOutputIdentifier, true);
        return Util.coalesce(wasSpent, false);
    }

    public List<TransactionOutputIdentifier> getSpentOutputs() {
        return new ImmutableList<TransactionOutputIdentifier>(_spentOutputs.keySet());
    }
}
