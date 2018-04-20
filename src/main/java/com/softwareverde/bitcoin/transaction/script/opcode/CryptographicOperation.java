package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.runner.context.Context;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.stack.ScriptSignature;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.transaction.signer.SignatureContext;
import com.softwareverde.bitcoin.transaction.signer.TransactionSigner;
import com.softwareverde.bitcoin.type.key.PublicKey;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.io.Logger;

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

    protected static Boolean checkSignature(final Context context, final PublicKey publicKey, final ScriptSignature scriptSignature) {
        final Transaction transaction = context.getTransaction();
        final Integer transactionInputIndexBeingSigned = context.getTransactionInputIndex();
        final TransactionOutput transactionOutputBeingSpent = context.getTransactionOutput();

        final TransactionSigner transactionSigner = new TransactionSigner();
        final SignatureContext signatureContext = new SignatureContext(transaction, scriptSignature.getHashType());
        signatureContext.setShouldSignInput(transactionInputIndexBeingSigned, true, transactionOutputBeingSpent);
        signatureContext.setLastCodeSeparatorIndex(transactionInputIndexBeingSigned, context.getScriptLastCodeSeparatorIndex());
        return transactionSigner.isSignatureValid(signatureContext, publicKey, scriptSignature);
    }

    @Override
    public Boolean applyTo(final Stack stack, final MutableContext context) {
        context.incrementCurrentLockingScriptIndex();

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
                final Integer postCodeSeparatorScriptIndex = context.getCurrentScriptIndex(); // NOTE: Context.CurrentLockingScriptIndex has already been incremented. So this value is one-past the current opcode index.
                context.setLockingScriptLastCodeSeparatorIndex(postCodeSeparatorScriptIndex);
                return true;
            }

            case CHECK_SIGNATURE_THEN_VERIFY:
            case CHECK_SIGNATURE: {
                final Value publicKeyValue = stack.pop();
                final Value signatureValue = stack.pop();

                if (stack.didOverflow()) { return false; }

                final ScriptSignature scriptSignature = signatureValue.asScriptSignature();
                if (scriptSignature == null) { return false; }

                final PublicKey publicKey = publicKeyValue.asPublicKey();

                final Boolean signatureIsValid = checkSignature(context, publicKey, scriptSignature);

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
                Logger.log(stack);

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

                final List<ScriptSignature> signatures;
                {
                    final ImmutableListBuilder<ScriptSignature> listBuilder = new ImmutableListBuilder<ScriptSignature>();
                    for (int i = 0; i < signatureCount; ++i) {
                        final Value signatureValue = stack.pop();
                        final ScriptSignature signature = signatureValue.asScriptSignature();
                        if (signature == null) { return false; }

                        listBuilder.add(signature);
                    }
                    signatures = listBuilder.build();
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
                            final boolean signatureIsValid = checkSignature(context, publicKey, signature);
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
