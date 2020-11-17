package com.softwareverde.bitcoin.transaction.signer;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.Mode;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.Util;

public class SignatureContext {
    protected final Transaction _transaction;
    protected final HashType _hashType;
    protected final Long _blockHeight;

    protected final UpgradeSchedule _upgradeSchedule;
    protected final MutableList<Boolean> _inputScriptsToSign = new MutableList<Boolean>(); // Determines if the script is left intact or replaced with an empty script...
    protected final MutableList<TransactionOutput> _previousTransactionOutputsBeingSpent = new MutableList<TransactionOutput>();
    protected final MutableList<Integer> _codeSeparatorIndexes = new MutableList<Integer>();

    protected Integer _inputIndexBeingSigned = null;
    protected Script _currentScript;
    protected List<ByteArray> _bytesToExcludeFromScript = new MutableList<ByteArray>();

    public SignatureContext(final Transaction transaction, final HashType hashType, final UpgradeSchedule upgradeSchedule) {
        this(transaction, hashType, Long.MAX_VALUE, upgradeSchedule);
    }

    public SignatureContext(final Transaction transaction, final HashType hashType, final Long blockHeight, final UpgradeSchedule upgradeSchedule) {
        _upgradeSchedule = upgradeSchedule;
        _transaction = transaction;
        _hashType = hashType;
        _blockHeight = blockHeight;

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        for (int i = 0; i < transactionInputs.getCount(); ++i) {
            _inputScriptsToSign.add(false); // All inputs are NOT signed by default...
            _previousTransactionOutputsBeingSpent.add(null);
            _codeSeparatorIndexes.add(0);
        }
    }

    public void setShouldSignInputScript(final Integer index, final Boolean shouldSignInput, final TransactionOutput outputBeingSpent) {
        _inputScriptsToSign.set(index, shouldSignInput);
        _previousTransactionOutputsBeingSpent.set(index, outputBeingSpent);
        _codeSeparatorIndexes.set(index, 0);
    }

    public void setLastCodeSeparatorIndex(final Integer index, final Integer lastCodeSeparatorIndex) {
        _codeSeparatorIndexes.set(index, lastCodeSeparatorIndex);
    }

    public void setInputIndexBeingSigned(final Integer inputIndex) {
        _inputIndexBeingSigned = inputIndex;
    }

    public void setCurrentScript(final Script script) {
        _currentScript = script;
    }

    public void setBytesToExcludeFromScript(final List<ByteArray> bytesToExcludeFromScript) {
        _bytesToExcludeFromScript = Util.coalesce(bytesToExcludeFromScript, _bytesToExcludeFromScript).asConst(); // NOTE: Ensure _bytesToExcludeFromScript is never null...
    }

    public Transaction getTransaction() {
        return _transaction;
    }

    public HashType getHashType() {
        return _hashType;
    }

    public Boolean shouldInputScriptBeSigned(final Integer inputIndex) {
        return _inputScriptsToSign.get(inputIndex);
    }

    public Boolean shouldInputBeSigned(final Integer inputIndex) {
        if (! _hashType.shouldSignOtherInputs()) {
            // SIGHASH_ANYONECANPAY
            return (inputIndex.intValue() == _inputIndexBeingSigned.intValue());
        }

        return true;
    }

    public Boolean shouldInputSequenceNumberBeSigned(final Integer inputIndex) {
        final Mode mode = _hashType.getMode();
        if ( (mode == Mode.SIGNATURE_HASH_SINGLE) || (mode == Mode.SIGNATURE_HASH_NONE) ) {
            if (inputIndex.intValue() != _inputIndexBeingSigned.intValue()) {
                return false;
            }
        }

        return true;
    }

    public Boolean shouldOutputBeSigned(final Integer outputIndex) {
        final Mode signatureMode = _hashType.getMode();
        if (signatureMode == Mode.SIGNATURE_HASH_NONE) {
            return false;
        }

        if (signatureMode == Mode.SIGNATURE_HASH_SINGLE) {
            return (outputIndex <= _inputIndexBeingSigned);
        }

        return true;
    }

    public Boolean shouldOutputAmountBeSigned(final Integer outputIndex) {
        final Mode signatureMode = _hashType.getMode();
        if (signatureMode == Mode.SIGNATURE_HASH_SINGLE) {
            if (outputIndex.intValue() != _inputIndexBeingSigned.intValue()) {
                return false;
            }
        }

        return true;
    }

    public Boolean shouldOutputScriptBeSigned(final Integer outputIndex) {
        final Mode signatureMode = _hashType.getMode();
        if (signatureMode == Mode.SIGNATURE_HASH_SINGLE) {
            if (outputIndex.intValue() != _inputIndexBeingSigned.intValue()) {
                return false;
            }
        }

        return true;
    }

    public TransactionOutput getTransactionOutputBeingSpent(final Integer inputIndex) {
        return _previousTransactionOutputsBeingSpent.get(inputIndex);
    }

    public Integer getLastCodeSeparatorIndex(final Integer index) {
        return _codeSeparatorIndexes.get(index);
    }

    public Integer getInputIndexBeingSigned() {
        return _inputIndexBeingSigned;
    }

    public Script getCurrentScript() {
        return _currentScript;
    }

    public List<ByteArray> getBytesToExcludeFromScript() {
        return _bytesToExcludeFromScript;
    }

    public Boolean shouldUseBitcoinCashSigningAlgorithm() {
        final Boolean bitcoinCashSignatureHashIsEnabled = _upgradeSchedule.isBitcoinCashSignatureHashTypeEnabled(_blockHeight);
        if (! bitcoinCashSignatureHashIsEnabled) { return false; }

        return _hashType.isBitcoinCashType();
    }
}
