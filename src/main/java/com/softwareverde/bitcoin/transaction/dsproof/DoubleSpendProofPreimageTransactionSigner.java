package com.softwareverde.bitcoin.transaction.dsproof;

import com.softwareverde.bitcoin.server.message.type.dsproof.DoubleSpendProofPreimage;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.transaction.signer.BitcoinCashTransactionSignerUtil;
import com.softwareverde.bitcoin.transaction.signer.SignatureContext;
import com.softwareverde.bitcoin.transaction.signer.TransactionSigner;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.util.bytearray.ByteArrayBuilder;

public class DoubleSpendProofPreimageTransactionSigner extends TransactionSigner {
    protected final DoubleSpendProofPreimage _doubleSpendProofPreimage;

    @Override
    protected byte[] _getBitcoinCashBytesForSigning(final SignatureContext signatureContext) {
        final BitcoinCashSignaturePreimage defaultSignaturePreimage = _getBitcoinCashPreimage(signatureContext);
        final HashType hashType = signatureContext.getHashType();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        { // 1. Serialize this Transaction's version...
            final Long transactionVersion = _doubleSpendProofPreimage.getTransactionVersion();
            final ByteArray versionBytes = BitcoinCashTransactionSignerUtil.getTransactionVersionBytes(transactionVersion);
            byteArrayBuilder.appendBytes(versionBytes);
        }

        { // 2. Serialize this Transaction's PreviousTransactionOutputs...
            final ByteArray previousOutputsDigest = _doubleSpendProofPreimage.getPreviousOutputsDigest();
            byteArrayBuilder.appendBytes(previousOutputsDigest);
        }

        { // 3. Serialize this Transaction's Inputs' SequenceNumbers...
            final ByteArray sequenceNumbersDigest;
            final Boolean sequenceNumbersDigestShouldBeEmptyHash = BitcoinCashTransactionSignerUtil.shouldSequenceNumbersDigestBeEmptyHash(hashType);
            if (sequenceNumbersDigestShouldBeEmptyHash) {
                sequenceNumbersDigest = Sha256Hash.EMPTY_HASH;
            }
            else {
                sequenceNumbersDigest = _doubleSpendProofPreimage.getSequenceNumbersDigest();
            }
            byteArrayBuilder.appendBytes(sequenceNumbersDigest);
        }

        { // 4. Serialize the TransactionInput's PreviousTransactionOutput...
            byteArrayBuilder.appendBytes(defaultSignaturePreimage.previousOutputBytes);
        }

        { // 5. Serialize the script...
            byteArrayBuilder.appendBytes(defaultSignaturePreimage.scriptBytes);
        }

        { // 6. Serialize the amount of the spent TransactionOutput...
            byteArrayBuilder.appendBytes(defaultSignaturePreimage.transactionOutputAmountBytes);
        }

        { // 7. Serialize the SequenceNumber for this TransactionInput...
            final SequenceNumber sequenceNumber = _doubleSpendProofPreimage.getSequenceNumber();
            byteArrayBuilder.appendBytes(sequenceNumber.getBytes());
        }

        { // 8. Serialize this Transaction's TransactionOutputs...
            final ByteArray transactionOutputsDigest = _doubleSpendProofPreimage.getTransactionOutputsDigest(hashType);
            byteArrayBuilder.appendBytes(transactionOutputsDigest);
        }

        { // 9. Serialize this Transaction's LockTime...
            final LockTime lockTime = _doubleSpendProofPreimage.getLockTime();
            final ByteArray lockTimeBytes = BitcoinCashTransactionSignerUtil.getTransactionLockTimeBytes(lockTime);
            byteArrayBuilder.appendBytes(lockTimeBytes);
        }

        { // 10. Serialize this Transaction's HashType...
            byteArrayBuilder.appendBytes(defaultSignaturePreimage.hashTypeBytes);
        }

        return HashUtil.doubleSha256(byteArrayBuilder.build());
    }

    public DoubleSpendProofPreimageTransactionSigner(final DoubleSpendProofPreimage doubleSpendProofPreimage) {
        _doubleSpendProofPreimage = doubleSpendProofPreimage;
    }
}
