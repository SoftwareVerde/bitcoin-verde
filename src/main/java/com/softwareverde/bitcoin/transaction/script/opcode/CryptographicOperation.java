package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.runner.context.Context;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.signature.ScriptSignature;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.transaction.signer.SignatureContext;
import com.softwareverde.bitcoin.transaction.signer.TransactionSigner;
import com.softwareverde.bitcoin.type.key.PublicKey;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class CryptographicOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_CRYPTOGRAPHIC;

    protected static CryptographicOperation fromBytes(final ByteArrayReader byteArrayReader) {
        if (! byteArrayReader.hasBytes()) { return null; }

        final byte opcodeByte = byteArrayReader.readByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final Opcode opcode = TYPE.getSubtype(opcodeByte);
        if (opcode == null) { return null; }

        return new CryptographicOperation(opcodeByte, opcode);
    }

    protected CryptographicOperation(final byte value, final Opcode opcode) {
        super(value, TYPE, opcode);
    }

    protected static Boolean checkSignature(final Context context, final PublicKey publicKey, final ScriptSignature scriptSignature, final List<ByteArray> bytesToExcludeFromScript) {
        final Transaction transaction = context.getTransaction();
        final Integer transactionInputIndexBeingSigned = context.getTransactionInputIndex();
        final TransactionOutput transactionOutputBeingSpent = context.getTransactionOutput();
        final Integer codeSeparatorIndex = context.getScriptLastCodeSeparatorIndex();
        final Script currentScript = context.getCurrentScript();

        final TransactionSigner transactionSigner = new TransactionSigner();
        final SignatureContext signatureContext = new SignatureContext(transaction, scriptSignature.getHashType());
        signatureContext.setInputIndexBeingSigned(transactionInputIndexBeingSigned);
        signatureContext.setShouldSignInputScript(transactionInputIndexBeingSigned, true, transactionOutputBeingSpent);
        signatureContext.setLastCodeSeparatorIndex(transactionInputIndexBeingSigned, codeSeparatorIndex);
        signatureContext.setCurrentScript(currentScript);
        signatureContext.setBytesToExcludeFromScript(bytesToExcludeFromScript);
        return transactionSigner.isSignatureValid(signatureContext, publicKey, scriptSignature);
    }

    @Override
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableContext context) {
        switch (_opcode) {
            case RIPEMD_160: {
                final Value input = stack.pop();
                final byte[] bytes = BitcoinUtil.ripemd160(input.getBytes());
                stack.push(Value.fromBytes(bytes));

                return (! stack.didOverflow());
            }

            case SHA_1: {
                final Value input = stack.pop();
                final byte[] bytes = BitcoinUtil.sha1(input.getBytes());
                stack.push(Value.fromBytes(bytes));

                return (! stack.didOverflow());
            }

            case SHA_256: {
                final Value input = stack.pop();
                final byte[] bytes = BitcoinUtil.sha256(input.getBytes());
                stack.push(Value.fromBytes(bytes));

                return (! stack.didOverflow());
            }

            case SHA_256_THEN_RIPEMD_160: {
                final Value input = stack.pop();
                final byte[] bytes = BitcoinUtil.ripemd160(BitcoinUtil.sha256(input.getBytes()));
                stack.push(Value.fromBytes(bytes));

                return (! stack.didOverflow());
            }

            case DOUBLE_SHA_256: {
                final Value input = stack.pop();
                final byte[] bytes = BitcoinUtil.sha256(BitcoinUtil.sha256(input.getBytes()));
                stack.push(Value.fromBytes(bytes));

                return (! stack.didOverflow());
            }

            case CODE_SEPARATOR: {
                final Integer postCodeSeparatorScriptIndex = context.getScriptIndex(); // NOTE: Context.CurrentLockingScriptIndex has already been incremented. So this value is one-past the current opcode index.
                context.setCurrentScriptLastCodeSeparatorIndex(postCodeSeparatorScriptIndex);
                return true;
            }

            case CHECK_SIGNATURE_THEN_VERIFY:
            case CHECK_SIGNATURE: {
                final Value publicKeyValue = stack.pop();
                final Value signatureValue = stack.pop();

                if (stack.didOverflow()) { return false; }

                final List<ByteArray> bytesToRemoveFromScript;
                { // NOTE: All instances of the signature should be purged from the signed script...
                    final ImmutableListBuilder<ByteArray> signatureBytesBuilder = new ImmutableListBuilder<ByteArray>(1);
                    signatureBytesBuilder.add(MutableByteArray.wrap(signatureValue.getBytes()));
                    bytesToRemoveFromScript = signatureBytesBuilder.build();
                }

                final Boolean signatureIsValid;
                {
                    final ScriptSignature scriptSignature = signatureValue.asScriptSignature();
                    if (scriptSignature != null) {
                        final PublicKey publicKey = publicKeyValue.asPublicKey();
                        signatureIsValid = checkSignature(context, publicKey, scriptSignature, bytesToRemoveFromScript);
                    }
                    else {
                        // NOTE: An invalid scriptSignature is permitted, and just simply fails...
                        //  Example Transaction: 9FB65B7304AAA77AC9580823C2C06B259CC42591E5CCE66D76A81B6F51CC5C28
                        signatureIsValid = false;
                    }
                }

                if (_opcode == Opcode.CHECK_SIGNATURE_THEN_VERIFY) {
                    if (! signatureIsValid) { return false; }
                }
                else {
                    stack.push(Value.fromBoolean(signatureIsValid));
                }

                return (! stack.didOverflow());
            }

            case CHECK_MULTISIGNATURE:
            case CHECK_MULTISIGNATURE_THEN_VERIFY: {
                final Integer publicKeyCount;
                {
                    final Value publicKeyCountValue = stack.pop();
                    publicKeyCount = publicKeyCountValue.asInteger();
                }

                final List<PublicKey> publicKeys;
                {
                    final ImmutableListBuilder<PublicKey> listBuilder = new ImmutableListBuilder<PublicKey>();
                    for (int i = 0; i < publicKeyCount; ++i) {
                        final Value publicKeyValue = stack.pop();
                        final PublicKey publicKey = publicKeyValue.asPublicKey();
                        listBuilder.add(publicKey);
                    }
                    publicKeys = listBuilder.build();
                }

                final Integer signatureCount;
                {
                    final Value signatureCountValue = stack.pop();
                    signatureCount = signatureCountValue.asInteger();
                }

                final List<ByteArray> bytesToRemoveFromScript;
                final List<ScriptSignature> signatures;
                {
                    final ImmutableListBuilder<ByteArray> signatureBytesBuilder = new ImmutableListBuilder<ByteArray>(signatureCount);
                    final ImmutableListBuilder<ScriptSignature> listBuilder = new ImmutableListBuilder<ScriptSignature>(signatureCount);
                    for (int i = 0; i < signatureCount; ++i) {
                        final Value signatureValue = stack.pop();
                        final ScriptSignature signature = signatureValue.asScriptSignature();
                        // if (signature == null) { return false; } // NOTE: An invalid scriptSignature is permitted, and just simply fails...

                        signatureBytesBuilder.add(MutableByteArray.wrap(signatureValue.getBytes())); // NOTE: All instances of the signature should be purged from the signed script...
                        listBuilder.add(signature);
                    }
                    signatures = listBuilder.build();
                    bytesToRemoveFromScript = signatureBytesBuilder.build();
                }

                stack.pop(); // Pop an extra value due to bug in the protocol...


                final boolean signaturesAreValid;
                {   // Signatures must appear in the same order as their paired public key, but the number of signatures may be less than the number of public keys.
                    // Example: P1, P2, P3 <-> S2, S3
                    //          P1, P2, P3 <-> S1, S3
                    //          P1, P2, P3 <-> S1, S2
                    //          P1, P2, P3 <-> S1, S2, S3

                    boolean signaturesHaveMatchedPublicKeys = true;
                    int nextPublicKeyIndex = 0;
                    for (int i = 0; i < signatureCount; ++i) {
                        final ScriptSignature signature = signatures.get(i);

                        boolean signatureHasPublicKeyMatch = false;
                        for (int j = nextPublicKeyIndex; j < publicKeyCount; ++j) {
                            nextPublicKeyIndex += 1;

                            final PublicKey publicKey = publicKeys.get(j);
                            final boolean signatureIsValid;
                            {
                                if (signature != null) {
                                    signatureIsValid = checkSignature(context, publicKey, signature, bytesToRemoveFromScript);
                                }
                                else {
                                    signatureIsValid = false; // NOTE: An invalid scriptSignature is permitted, and just simply fails...
                                }
                            }
                            if (signatureIsValid) {
                                signatureHasPublicKeyMatch = true;
                                break;
                            }
                        }

                        if (! signatureHasPublicKeyMatch) {
                            signaturesHaveMatchedPublicKeys = false;
                            break;
                        }
                    }

                    signaturesAreValid = signaturesHaveMatchedPublicKeys;
                }

                if (_opcode == Opcode.CHECK_MULTISIGNATURE_THEN_VERIFY) {
                    if (! signaturesAreValid) { return false; }
                }
                else {
                    stack.push(Value.fromBoolean(signaturesAreValid));
                    // stack.push(Value.fromBoolean(true));
                }

                return (! stack.didOverflow());
            }

            default: { return false; }
        }
    }
}
