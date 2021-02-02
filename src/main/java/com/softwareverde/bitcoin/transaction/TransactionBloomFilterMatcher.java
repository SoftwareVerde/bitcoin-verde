package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.merkleroot.MerkleTree;
import com.softwareverde.bitcoin.bloomfilter.UpdateBloomFilterMode;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;

public class TransactionBloomFilterMatcher implements MerkleTree.Filter<Transaction> {
    protected final AddressInflater _addressInflater;
    protected final BloomFilter _bloomFilter;
    protected final UpdateBloomFilterMode _updateBloomFilterMode;

    protected static class BloomFilterMatchResult {
        public final Boolean didMatch;
        public final List<ByteArray> itemsToAddToBloomFilter;

        protected BloomFilterMatchResult(final Boolean didMatch, final List<ByteArray> itemsToAddToBloomFilter) {
            this.didMatch = didMatch;
            this.itemsToAddToBloomFilter = itemsToAddToBloomFilter;
        }
    }

    protected BloomFilterMatchResult _matchesBloomFilter(final Transaction transaction) {
        final MutableList<ByteArray> itemsToAddToBloomFilter = new MutableList<ByteArray>();
        if (_bloomFilter == null) {
            return new BloomFilterMatchResult(true, itemsToAddToBloomFilter);
        }

        boolean didMatch = false;

        // From BIP37: https://github.com/bitcoin/bips/blob/master/bip-0037.mediawiki
        final Sha256Hash transactionHash = transaction.getHash();
        if (_bloomFilter.containsItem(transactionHash)) { // 1. Test the hash of the transaction itself.
            didMatch = true;
        }

        int transactionOutputIndex = 0;
        for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) { // 2. For each output, test each data element of the output script. This means each hash and key in the output script is tested independently.
            final LockingScript lockingScript = transactionOutput.getLockingScript();
            for (final Operation operation : lockingScript.getOperations()) {
                if (operation.getType() != PushOperation.TYPE) { continue; }

                final ByteArray value = ((PushOperation) operation).getValue();
                if (_bloomFilter.containsItem(value)) {
                    boolean shouldUpdateBloomFilter = false;

                    if (_updateBloomFilterMode == UpdateBloomFilterMode.UPDATE_ALL) {
                        shouldUpdateBloomFilter = true;
                    }
                    else if (_updateBloomFilterMode == UpdateBloomFilterMode.P2PK_P2MS) {
                        final ScriptType scriptType = lockingScript.getScriptType();
                        // NOTE: Bitcoin Verde is not updating the bloom filter for MULTISIG scripts as it currently doesn't recognize the raw MULTISIG script type
                        //       Other implementations do not include P2SH scripts, while Bitcoin Verde does
                        if ( (scriptType == ScriptType.PAY_TO_PUBLIC_KEY_HASH) || (scriptType == ScriptType.PAY_TO_SCRIPT_HASH) ) {
                            shouldUpdateBloomFilter = true;
                        }
                    }

                    if (shouldUpdateBloomFilter) {
                        final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, transactionOutputIndex);
                        itemsToAddToBloomFilter.add(transactionOutputIdentifier.toBytes());
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

            if (_bloomFilter.containsItem(cOutpoint)) { didMatch = true; }

            // 4. For each input, test each data element of the input script.
            final UnlockingScript unlockingScript = transactionInput.getUnlockingScript();
            for (final Operation operation : unlockingScript.getOperations()) {
                if (operation.getType() != PushOperation.TYPE) { continue; }

                final Value value = ((PushOperation) operation).getValue();
                if (_bloomFilter.containsItem(value)) {
                    didMatch = true;
                    break; // Prevout matches do not add to the matchedItems set...
                }

                if (_addressInflater != null) { // If the value could be a public-key, then check if its Address-form matches the value... (Verde-Specific Behavior)
                    final PublicKey publicKey = value.asPublicKey();
                    if (publicKey == null) { continue; }
                    if (! publicKey.isValid()) { continue; }

                    final Address address = _addressInflater.fromPublicKey(publicKey);
                    if ( (address != null) && _bloomFilter.containsItem(publicKey) ) {
                        didMatch = true;
                        break; // Prevout matches do not add to the matchedItems set...
                    }
                }
            }
        }

        return new BloomFilterMatchResult(didMatch, itemsToAddToBloomFilter);
    }

    public TransactionBloomFilterMatcher(final BloomFilter bloomFilter, final AddressInflater addressInflater) {
        _addressInflater = addressInflater;
        _bloomFilter = bloomFilter.asConst();
        _updateBloomFilterMode = UpdateBloomFilterMode.READ_ONLY;
    }

    public TransactionBloomFilterMatcher(final MutableBloomFilter mutableBloomFilter, final UpdateBloomFilterMode updateBloomFilterMode, final AddressInflater addressInflater) {
        _addressInflater = addressInflater;
        _bloomFilter = mutableBloomFilter;
        _updateBloomFilterMode = updateBloomFilterMode;
    }

    @Override
    public boolean shouldInclude(final Transaction transaction) {
        final BloomFilterMatchResult bloomFilterMatchResult = _matchesBloomFilter(transaction);
        if (! bloomFilterMatchResult.didMatch) { return false; }

        if (_updateBloomFilterMode != UpdateBloomFilterMode.READ_ONLY) {
            final MutableBloomFilter mutableBloomFilter = (MutableBloomFilter) _bloomFilter;
            for (final ByteArray item : bloomFilterMatchResult.itemsToAddToBloomFilter) {
                mutableBloomFilter.addItem(item);
            }
        }
        return true;
    }
}
