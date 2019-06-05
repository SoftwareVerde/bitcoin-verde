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

public class SlpUtil {

    /**
     * Returns true iff transactionOutputIdentifier points to an output containing SLP tokens.
     *  This only includes Genesis Receiver, Baton Receiver, and Send outputs.
     */
    public static Boolean outputContainsSpendableSlpTokens(final Transaction transaction, final Integer transactionOutputIndex) {
        final Long tokenAmount = SlpUtil.getOutputTokenAmount(transaction, transactionOutputIndex);
        return (tokenAmount > 0);
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

                // Check for the output being the generator...
                final SlpGenesisScript slpGenesisScript = slpScriptInflater.genesisScriptFromScript(lockingScript);
                if (slpGenesisScript == null) { return false; }

                final Boolean isGenerator = Util.areEqual(transactionOutputIndex, slpGenesisScript.getGeneratorOutputIndex());
                if (isGenerator) { return true; }
            } break;

            case MINT: {
                // Check for the output containing mint tokens...
                final Boolean isMintReceiver = Util.areEqual(transactionOutputIndex, SlpMintScript.RECEIVER_TRANSACTION_OUTPUT_INDEX);
                if (isMintReceiver) { return true; }

                // Check for the output being the generator...
                final SlpMintScript slpMintScript = slpScriptInflater.mintScriptFromScript(lockingScript);
                if (slpMintScript == null) { return false; }

                final Boolean isGenerator = Util.areEqual(transactionOutputIndex, slpMintScript.getGeneratorOutputIndex());
                if (isGenerator) { return true; }
            } break;

            case SEND: {
                // Check for the output containing tokens sent from another output...
                final SlpSendScript slpSendScript = slpScriptInflater.sendScriptFromScript(lockingScript);
                if (slpSendScript == null) { return false; }

                final Long tokenAmount = Util.coalesce(slpSendScript.getAmount(transactionOutputIndex));
                if (tokenAmount > 0) { return true; }
            } break;
        }

        return false;
    }

    public static Long getOutputTokenAmount(final Transaction transaction, final Integer transactionOutputIndex) {
        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        final TransactionOutput transactionOutput = transactionOutputs.get(0);
        final LockingScript lockingScript = transactionOutput.getLockingScript();

        final SlpScriptType slpScriptType = SlpScriptInflater.getScriptType(lockingScript);
        if (slpScriptType == null) { return 0L; } // Transaction is not an SLP transaction.

        final SlpScriptInflater slpScriptInflater = new SlpScriptInflater();

        switch (slpScriptType) {
            case GENESIS: {
                // Check for the output containing genesis tokens...
                final Boolean isGenesisReceiver = Util.areEqual(transactionOutputIndex, SlpGenesisScript.RECEIVER_TRANSACTION_OUTPUT_INDEX);
                if (! isGenesisReceiver) { return 0L; }

                final SlpGenesisScript slpGenesisScript = slpScriptInflater.genesisScriptFromScript(lockingScript);
                if (slpGenesisScript == null) { return 0L; }

                return slpGenesisScript.getTokenCount();
            }

            case MINT: {
                // Check for the output containing mint tokens...
                final Boolean isMintReceiver = Util.areEqual(transactionOutputIndex, SlpMintScript.RECEIVER_TRANSACTION_OUTPUT_INDEX);
                if (! isMintReceiver) { return 0L; }

                final SlpMintScript slpMintScript = slpScriptInflater.mintScriptFromScript(lockingScript);
                if (slpMintScript == null) { return 0L; }

                return slpMintScript.getTokenCount();
            }

            case SEND: {
                // Check for the output containing tokens sent from another output...
                final SlpSendScript slpSendScript = slpScriptInflater.sendScriptFromScript(lockingScript);
                if (slpSendScript == null) { return 0L; }

                return Util.coalesce(slpSendScript.getAmount(transactionOutputIndex));
            }
        }

        return 0L;
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

                return Util.areEqual(slpGenesisScript.getGeneratorOutputIndex(), transactionOutputIndex);
            }

            case MINT: {
                final SlpMintScript slpMintScript = slpScriptInflater.mintScriptFromScript(lockingScript);
                if (slpMintScript == null) { return false; }

                return Util.areEqual(slpMintScript.getGeneratorOutputIndex(), transactionOutputIndex);
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
