package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.block.merkleroot.Hashable;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.transaction.coinbase.CoinbaseTransaction;
import com.softwareverde.bitcoin.transaction.input.CoinbaseTransactionInputInflater;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptInflater;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.constable.Constable;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.json.Jsonable;

public interface Transaction extends Hashable, Constable<ConstTransaction>, Jsonable {
    Long VERSION = BitcoinConstants.getTransactionVersion();
    Long SATOSHIS_PER_BITCOIN = 100_000_000L;

    static Boolean isCoinbaseTransaction(final Transaction transaction) {
        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        if (transactionInputs.getCount() != 1) { return false; }

        final TransactionInput transactionInput = transactionInputs.get(0);
        final boolean isCoinbaseInput = CoinbaseTransactionInputInflater.isCoinbaseInput(transactionInput);
        if (! isCoinbaseInput) { return false; }

        return true;
    }

    static Boolean isSlpTransaction(final Transaction transaction) {
        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        final TransactionOutput transactionOutput = transactionOutputs.get(0);
        return SlpScriptInflater.matchesSlpFormat(transactionOutput.getLockingScript());
    }

    /**
     * Returns true if transaction matches matches _bloomFilter or if _bloomFilter has not been set.
     */
    static Boolean matchesFilter(final Transaction transaction, final BloomFilter bloomFilter) {
        if (bloomFilter == null) { return true; }

        // From BIP37: https://github.com/bitcoin/bips/blob/master/bip-0037.mediawiki
        // To determine if a transaction matches the filter, the following algorithm is used. Once a match is found the algorithm aborts.
        final Sha256Hash transactionHash = transaction.getHash();
        if (bloomFilter.containsItem(transactionHash)) { return true; } // 1. Test the hash of the transaction itself.

        int transactionOutputIndex = 0;
        for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) { // 2. For each output, test each data element of the output script. This means each hash and key in the output script is tested independently.
            final LockingScript lockingScript = transactionOutput.getLockingScript();
            for (final Operation operation : lockingScript.getOperations()) {
                if (operation.getType() != PushOperation.TYPE) { continue; }

                final ByteArray value = ((PushOperation) operation).getValue();
                if (bloomFilter.containsItem(value)) {
                    return true;
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

            if (bloomFilter.containsItem(cOutpoint)) { return true; }

            // 4. For each input, test each data element of the input script.
            final UnlockingScript unlockingScript = transactionInput.getUnlockingScript();
            for (final Operation operation : unlockingScript.getOperations()) {
                if (operation.getType() != PushOperation.TYPE) { continue; }

                final ByteArray value = ((PushOperation) operation).getValue();
                if (bloomFilter.containsItem(value)) { return true; }
            }
        }

        return false;
    }

    Long getVersion();
    List<TransactionInput> getTransactionInputs();
    List<TransactionOutput> getTransactionOutputs();
    LockTime getLockTime();
    Long getTotalOutputValue();
    Boolean matches(BloomFilter bloomFilter);
    CoinbaseTransaction asCoinbase();

    Integer getByteCount();

    @Override
    ConstTransaction asConst();
}
