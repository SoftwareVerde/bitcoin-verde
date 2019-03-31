package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.bloomfilter.UpdateBloomFilterMode;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;

public class TransactionBloomFilterMatcher {
    /**
     * Returns null if no parts of the transaction matched the BloomFilter.
     * Otherwise, returns the list of ByteArrays that matched the BloomFilter.
     *  If bloomFilter is null, a match always occurs.
     *  NOTE: An empty list is an indication that a match occurred.
     */
    protected List<ByteArray> _getMatchedItems(final Transaction transaction, final BloomFilter bloomFilter, final UpdateBloomFilterMode updateBloomFilterMode) {
        final MutableList<ByteArray> matchedItems = new MutableList<ByteArray>();
        if (bloomFilter == null) { return matchedItems; } // TRUE

        // From BIP37: https://github.com/bitcoin/bips/blob/master/bip-0037.mediawiki
        // To determine if a transaction matches the filter, the following algorithm is used. Once a match is found the algorithm aborts.
        final Sha256Hash transactionHash = transaction.getHash();
        if (bloomFilter.containsItem(transactionHash)) { return matchedItems; } // 1. Test the hash of the transaction itself.

        boolean didMatch = false;

        int transactionOutputIndex = 0;
        for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) { // 2. For each output, test each data element of the output script. This means each hash and key in the output script is tested independently.
            final LockingScript lockingScript = transactionOutput.getLockingScript();
            for (final Operation operation : lockingScript.getOperations()) {
                if (operation.getType() != PushOperation.TYPE) { continue; }

                final ByteArray value = ((PushOperation) operation).getValue();
                if (bloomFilter.containsItem(value)) {
                    boolean shouldUpdateBloomFilter = false;

                    if (updateBloomFilterMode == UpdateBloomFilterMode.UPDATE_ALL) {
                        shouldUpdateBloomFilter = true;
                    }
                    else if (updateBloomFilterMode == UpdateBloomFilterMode.P2PK_P2MS) {
                        final ScriptType scriptType = lockingScript.getScriptType();
                        if ( (scriptType == ScriptType.PAY_TO_PUBLIC_KEY_HASH) || (scriptType == ScriptType.PAY_TO_SCRIPT_HASH)) {
                            shouldUpdateBloomFilter = true;
                        }
                    }

                    if (shouldUpdateBloomFilter) {
                        final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, transactionOutputIndex);
                        matchedItems.add(transactionOutputIdentifier.toBytes());
                    }

                    didMatch = true;
                }
            }

            transactionOutputIndex += 1;
        }

        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            // 3. For each input, test the serialized COutPoint structure.
            final ByteArray cOutpoint;
            { // NOTE: "COutPoint" is a class in the reference client that follows the same serialization process as the a TransactionInput's prevout format (TxHash | OutputIndex, both as LittleEndian)...
                final Sha256Hash previousOutputTransactionHash = transactionInput.getPreviousOutputTransactionHash();
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(previousOutputTransactionHash, transactionInput.getPreviousOutputIndex());
                cOutpoint = transactionOutputIdentifier.toBytes();
            }

            if (bloomFilter.containsItem(cOutpoint)) { didMatch = true; }

            // 4. For each input, test each data element of the input script.
            final UnlockingScript unlockingScript = transactionInput.getUnlockingScript();
            for (final Operation operation : unlockingScript.getOperations()) {
                if (operation.getType() != PushOperation.TYPE) { continue; }

                final ByteArray value = ((PushOperation) operation).getValue();
                if (bloomFilter.containsItem(value)) { didMatch = true; }
            }
        }

        return (didMatch ? matchedItems : null);
    }

    public Boolean matchesFilter(final Transaction transaction, final BloomFilter bloomFilter) {
        return (_getMatchedItems(transaction, bloomFilter, UpdateBloomFilterMode.READ_ONLY) != null);
    }

    public Boolean matchesFilterAndUpdate(final Transaction transaction, final MutableBloomFilter bloomFilter, final UpdateBloomFilterMode updateBloomFilterMode) {
        return (_getMatchedItems(transaction, bloomFilter, updateBloomFilterMode) != null);
    }
}
