package com.softwareverde.bitcoin.transaction.dsproof;

import com.softwareverde.bitcoin.server.message.type.dsproof.DoubleSpendProofPreimage;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.MutableScript;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.transaction.signer.SignatureContext;
import com.softwareverde.bitcoin.transaction.signer.TransactionSigner;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class DoubleSpendProofPreimageTransactionSigner extends TransactionSigner {
    protected final DoubleSpendProofPreimage _doubleSpendProofPreimage;

    @Override
    protected byte[] _getBitcoinCashBytesForSigning(final SignatureContext signatureContext) {
        final int FORK_ID = 0x000000;
        final Transaction transaction = signatureContext.getTransaction();
        final Integer inputIndex = signatureContext.getInputIndexBeingSigned();
        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        final HashType hashType = signatureContext.getHashType();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        { // 1. Serialize this Transaction's version...
            final Long transactionVersion = _doubleSpendProofPreimage.getTransactionVersion();
            byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(transactionVersion), Endian.LITTLE);
        }

        { // 2. Serialize this Transaction's PreviousTransactionOutputs...
            final ByteArray previousOutputsDigest = _doubleSpendProofPreimage.getPreviousOutputsDigest();
            byteArrayBuilder.appendBytes(previousOutputsDigest);
        }

        { // 3. Serialize this Transaction's Inputs' SequenceNumbers...
            final ByteArray sequenceNumbersDigest = _doubleSpendProofPreimage.getSequenceNumbersDigest();
            byteArrayBuilder.appendBytes(sequenceNumbersDigest);
        }

        { // 4. Serialize the TransactionInput's PreviousTransactionOutput...
            final TransactionInput transactionInput = transactionInputs.get(inputIndex);

            final ByteArrayBuilder serializedTransactionOutputBeingSpent = new ByteArrayBuilder();
            serializedTransactionOutputBeingSpent.appendBytes(transactionInput.getPreviousOutputTransactionHash(), Endian.LITTLE);
            serializedTransactionOutputBeingSpent.appendBytes(ByteUtil.integerToBytes(transactionInput.getPreviousOutputIndex()), Endian.LITTLE);

            byteArrayBuilder.appendBytes(serializedTransactionOutputBeingSpent.build());
        }

        { // 5. Serialize the script...
            final Script currentScript = signatureContext.getCurrentScript();
            final TransactionOutput transactionOutputBeingSpent = signatureContext.getTransactionOutputBeingSpent(inputIndex);
            final LockingScript outputBeingSpentLockingScript = transactionOutputBeingSpent.getLockingScript();

            final Script scriptForSigning;
            { // Handle Code-Separators...
                final MutableScript mutableScript = new MutableScript(Util.coalesce(currentScript, outputBeingSpentLockingScript));

                final Integer subscriptIndex = signatureContext.getLastCodeSeparatorIndex(inputIndex);
                if (subscriptIndex > 0) {
                    mutableScript.subScript(subscriptIndex);
                }

                // NOTE: (Subtly) According to Buip55 CODE_SEPARATOR are not removed in the new BCH serialization format.
                // mutableScript.removeOperations(Opcode.CODE_SEPARATOR);

                scriptForSigning = mutableScript;
            }

            byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(scriptForSigning.getByteCount()));
            byteArrayBuilder.appendBytes(scriptForSigning.getBytes());
        }

        { // 6. Serialize the amount of the spent TransactionOutput...
            final TransactionOutput transactionOutput = signatureContext.getTransactionOutputBeingSpent(inputIndex);
            byteArrayBuilder.appendBytes(ByteUtil.longToBytes(transactionOutput.getAmount()), Endian.LITTLE);
        }

        { // 7. Serialize the SequenceNumber for this TransactionInput...
            final SequenceNumber sequenceNumber = _doubleSpendProofPreimage.getSequenceNumber();
            byteArrayBuilder.appendBytes(sequenceNumber.getBytes(), Endian.LITTLE);
        }

        { // 8. Serialize this Transaction's TransactionOutputs...
            final ByteArray transactionOutputsDigest = _doubleSpendProofPreimage.getTransactionOutputsDigest();
            byteArrayBuilder.appendBytes(transactionOutputsDigest);
        }

        { // 9. Serialize this Transaction's LockTime...
            final LockTime lockTime = _doubleSpendProofPreimage.getLockTime();
            byteArrayBuilder.appendBytes(lockTime.getBytes(), Endian.LITTLE);
        }

        { // 10. Serialize this Transaction's HashType...
            final byte hashTypeByte = hashType.toByte();
            final byte[] hashTypeWithForkId = ByteUtil.integerToBytes(FORK_ID << 8);
            hashTypeWithForkId[3] |= hashTypeByte;
            byteArrayBuilder.appendBytes(hashTypeWithForkId, Endian.LITTLE);
        }

        return HashUtil.doubleSha256(byteArrayBuilder.build());
    }

    public DoubleSpendProofPreimageTransactionSigner(final DoubleSpendProofPreimage doubleSpendProofPreimage) {
        _doubleSpendProofPreimage = doubleSpendProofPreimage;
    }
}
