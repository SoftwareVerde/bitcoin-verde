package com.softwareverde.bitcoin.slp;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptInflater;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptType;
import com.softwareverde.bitcoin.transaction.script.slp.genesis.SlpGenesisScript;
import com.softwareverde.bitcoin.transaction.script.slp.mint.SlpMintScript;
import com.softwareverde.bitcoin.transaction.script.slp.send.SlpSendScript;
import com.softwareverde.constable.list.List;
import com.softwareverde.util.Util;

import java.math.BigInteger;

public class SlpUtil {

    /**
     * Returns true iff transactionOutputIdentifier points to an output containing SLP tokens.
     *  This only includes Genesis Receiver, Baton Receiver, and Send outputs.
     */
    public static Boolean outputContainsSpendableSlpTokens(final Transaction transaction, final Integer transactionOutputIndex) {
        final BigInteger tokenAmount = SlpUtil.getOutputTokenAmount(transaction, transactionOutputIndex);
        return (tokenAmount.compareTo(BigInteger.ZERO) > 0);
    }

    /**
     * Returns true iff transactionOutputIdentifier points to an output associated to SLP tokens.
     *  This includes Batons, Commits, Genesis, and Send outputs.
     */
    public static Boolean isSlpTokenOutput(final Transaction transaction, final Integer transactionOutputIndex) {
        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        final TransactionOutput transactionOutput = transactionOutputs.get(0);
        final LockingScript lockingScript = transactionOutput.getLockingScript();

        final SlpScriptType slpScriptType = SlpScriptInflater.getScriptType(lockingScript);
        if (slpScriptType == null) { return false; } // Transaction is not an SLP transaction.

        final SlpScriptInflater slpScriptInflater = new SlpScriptInflater();

        switch (slpScriptType) {
            case GENESIS: {
                // Check for the output containing genesis tokens...
                final Boolean isGenesisReceiver = Util.areEqual(transactionOutputIndex, SlpGenesisScript.RECEIVER_TRANSACTION_OUTPUT_INDEX);
                if (isGenesisReceiver) { return true; }

                // Check for the output being the baton...
                final SlpGenesisScript slpGenesisScript = slpScriptInflater.genesisScriptFromScript(lockingScript);
                if (slpGenesisScript == null) { return false; }

                final Boolean isBaton = Util.areEqual(transactionOutputIndex, slpGenesisScript.getBatonOutputIndex());
                if (isBaton) { return true; }
            } break;

            case MINT: {
                // Check for the output containing mint tokens...
                final Boolean isMintReceiver = Util.areEqual(transactionOutputIndex, SlpMintScript.RECEIVER_TRANSACTION_OUTPUT_INDEX);
                if (isMintReceiver) { return true; }

                // Check for the output being the baton...
                final SlpMintScript slpMintScript = slpScriptInflater.mintScriptFromScript(lockingScript);
                if (slpMintScript == null) { return false; }

                final Boolean isBaton = Util.areEqual(transactionOutputIndex, slpMintScript.getBatonOutputIndex());
                if (isBaton) { return true; }
            } break;

            case SEND: {
                // Check for the output containing tokens sent from another output...
                final SlpSendScript slpSendScript = slpScriptInflater.sendScriptFromScript(lockingScript);
                if (slpSendScript == null) { return false; }

                final BigInteger tokenAmount = slpSendScript.getAmount(transactionOutputIndex);
                if (tokenAmount != null && tokenAmount.compareTo(BigInteger.ZERO) > 0) { return true; }
            } break;
        }

        return false;
    }

    public static BigInteger getOutputTokenAmount(final Transaction transaction, final Integer transactionOutputIndex) {
        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        final TransactionOutput transactionOutput = transactionOutputs.get(0);
        final LockingScript lockingScript = transactionOutput.getLockingScript();

        final SlpScriptType slpScriptType = SlpScriptInflater.getScriptType(lockingScript);
        if (slpScriptType == null) { return BigInteger.ZERO; } // Transaction is not an SLP transaction.

        final SlpScriptInflater slpScriptInflater = new SlpScriptInflater();

        switch (slpScriptType) {
            case GENESIS: {
                // Check for the output containing genesis tokens...
                final Boolean isGenesisReceiver = Util.areEqual(transactionOutputIndex, SlpGenesisScript.RECEIVER_TRANSACTION_OUTPUT_INDEX);
                if (! isGenesisReceiver) { return BigInteger.ZERO; }

                final SlpGenesisScript slpGenesisScript = slpScriptInflater.genesisScriptFromScript(lockingScript);
                if (slpGenesisScript == null) { return BigInteger.ZERO; }

                return slpGenesisScript.getTokenCount();
            }

            case MINT: {
                // Check for the output containing mint tokens...
                final Boolean isMintReceiver = Util.areEqual(transactionOutputIndex, SlpMintScript.RECEIVER_TRANSACTION_OUTPUT_INDEX);
                if (! isMintReceiver) { return BigInteger.ZERO; }

                final SlpMintScript slpMintScript = slpScriptInflater.mintScriptFromScript(lockingScript);
                if (slpMintScript == null) { return BigInteger.ZERO; }

                return slpMintScript.getTokenCount();
            }

            case SEND: {
                // Check for the output containing tokens sent from another output...
                final SlpSendScript slpSendScript = slpScriptInflater.sendScriptFromScript(lockingScript);
                if (slpSendScript == null) { return BigInteger.ZERO; }

                final BigInteger amount = slpSendScript.getAmount(transactionOutputIndex);
                if (amount == null) { return BigInteger.ZERO; }
                return amount;
            }
        }

        return BigInteger.ZERO;
    }

    public static Boolean isSlpTokenBatonHolder(final Transaction transaction, final Integer transactionOutputIndex) {
        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        final TransactionOutput transactionOutput = transactionOutputs.get(0);
        final LockingScript lockingScript = transactionOutput.getLockingScript();

        final SlpScriptType slpScriptType = SlpScriptInflater.getScriptType(lockingScript);
        if (slpScriptType == null) { return false; } // Transaction is not an SLP transaction.

        final SlpScriptInflater slpScriptInflater = new SlpScriptInflater();

        switch (slpScriptType) {
            case GENESIS: {
                final SlpGenesisScript slpGenesisScript = slpScriptInflater.genesisScriptFromScript(lockingScript);
                if (slpGenesisScript == null) { return false; }

                return Util.areEqual(slpGenesisScript.getBatonOutputIndex(), transactionOutputIndex);
            }

            case MINT: {
                final SlpMintScript slpMintScript = slpScriptInflater.mintScriptFromScript(lockingScript);
                if (slpMintScript == null) { return false; }

                return Util.areEqual(slpMintScript.getBatonOutputIndex(), transactionOutputIndex);
            }
        }

        return false;
    }

    public static SlpTokenId getTokenId(final Transaction transaction) {
        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        final TransactionOutput transactionOutput = transactionOutputs.get(0);
        final LockingScript lockingScript = transactionOutput.getLockingScript();

        final SlpScriptType slpScriptType = SlpScriptInflater.getScriptType(lockingScript);
        if (slpScriptType == null) { return null; } // Transaction is not an SLP transaction.

        final SlpScriptInflater slpScriptInflater = new SlpScriptInflater();

        switch (slpScriptType) {
            case GENESIS: {
                return SlpTokenId.wrap(transaction.getHash());
            }

            case MINT: {
                final SlpMintScript slpMintScript = slpScriptInflater.mintScriptFromScript(lockingScript);
                if (slpMintScript == null) { return null; }

                return slpMintScript.getTokenId();
            }

            case SEND: {
                final SlpSendScript slpSendScript = slpScriptInflater.sendScriptFromScript(lockingScript);
                if (slpSendScript == null) { return null; }

                return slpSendScript.getTokenId();
            }
        }

        return null;
    }

    protected SlpUtil() { }
}
