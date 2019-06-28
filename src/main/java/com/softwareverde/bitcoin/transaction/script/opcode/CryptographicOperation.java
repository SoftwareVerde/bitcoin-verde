package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.bip.*;
import com.softwareverde.bitcoin.secp256k1.Schnorr;
import com.softwareverde.bitcoin.secp256k1.Secp256k1;
import com.softwareverde.bitcoin.secp256k1.key.PublicKey;
import com.softwareverde.bitcoin.secp256k1.signature.Signature;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
import com.softwareverde.bitcoin.transaction.script.runner.context.Context;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.signature.ScriptSignature;
import com.softwareverde.bitcoin.transaction.script.signature.ScriptSignatureContext;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.transaction.signer.SignatureContext;
import com.softwareverde.bitcoin.transaction.signer.TransactionSigner;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class CryptographicOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_CRYPTOGRAPHIC;
    public static final Integer MAX_MULTI_SIGNATURE_PUBLIC_KEY_COUNT = 20;

    public static final Boolean FAIL_ON_BAD_SIGNATURE_ENABLED = true; // "NULLFAIL"
    public static final Boolean REQUIRE_BITCOIN_CASH_FORK_ID = true; // "SIGHASH_FORKID" / "SCRIPT_ENABLE_SIGHASH_FORKID"

    public static final CryptographicOperation RIPEMD_160                       = new CryptographicOperation(Opcode.RIPEMD_160.getValue(),                          Opcode.RIPEMD_160);
    public static final CryptographicOperation SHA_1                            = new CryptographicOperation(Opcode.SHA_1.getValue(),                               Opcode.SHA_1);
    public static final CryptographicOperation SHA_256                          = new CryptographicOperation(Opcode.SHA_256.getValue(),                             Opcode.SHA_256);
    public static final CryptographicOperation SHA_256_THEN_RIPEMD_160          = new CryptographicOperation(Opcode.SHA_256_THEN_RIPEMD_160.getValue(),             Opcode.SHA_256_THEN_RIPEMD_160);
    public static final CryptographicOperation DOUBLE_SHA_256                   = new CryptographicOperation(Opcode.DOUBLE_SHA_256.getValue(),                      Opcode.DOUBLE_SHA_256);
    public static final CryptographicOperation CODE_SEPARATOR                   = new CryptographicOperation(Opcode.CODE_SEPARATOR.getValue(),                      Opcode.CODE_SEPARATOR);
    public static final CryptographicOperation CHECK_SIGNATURE                  = new CryptographicOperation(Opcode.CHECK_SIGNATURE.getValue(),                     Opcode.CHECK_SIGNATURE);
    public static final CryptographicOperation CHECK_SIGNATURE_THEN_VERIFY      = new CryptographicOperation(Opcode.CHECK_SIGNATURE_THEN_VERIFY.getValue(),         Opcode.CHECK_SIGNATURE_THEN_VERIFY);
    public static final CryptographicOperation CHECK_MULTISIGNATURE             = new CryptographicOperation(Opcode.CHECK_MULTISIGNATURE.getValue(),                Opcode.CHECK_MULTISIGNATURE);
    public static final CryptographicOperation CHECK_MULTISIGNATURE_THEN_VERIFY = new CryptographicOperation(Opcode.CHECK_MULTISIGNATURE_THEN_VERIFY.getValue(),    Opcode.CHECK_MULTISIGNATURE_THEN_VERIFY);
    public static final CryptographicOperation CHECK_DATA_SIGNATURE             = new CryptographicOperation(Opcode.CHECK_DATA_SIGNATURE.getValue(),                Opcode.CHECK_DATA_SIGNATURE);
    public static final CryptographicOperation CHECK_DATA_SIGNATURE_THEN_VERIFY = new CryptographicOperation(Opcode.CHECK_DATA_SIGNATURE_THEN_VERIFY.getValue(),    Opcode.CHECK_DATA_SIGNATURE_THEN_VERIFY);

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

    protected static Boolean verifySignature(final Context context, final PublicKey publicKey, final ScriptSignature scriptSignature, final List<ByteArray> bytesToExcludeFromScript) {
        final Transaction transaction = context.getTransaction();
        final Integer transactionInputIndexBeingSigned = context.getTransactionInputIndex();
        final TransactionOutput transactionOutputBeingSpent = context.getTransactionOutput();
        final Integer codeSeparatorIndex = context.getScriptLastCodeSeparatorIndex();
        final Script currentScript = context.getCurrentScript();

        final HashType hashType = scriptSignature.getHashType();

        final Long blockHeight = context.getBlockHeight();
        if (Buip55.isEnabled(blockHeight)) {
            if (! hashType.isBitcoinCashType()) {
                return false;
            }
        }

        final TransactionSigner transactionSigner = new TransactionSigner();
        final SignatureContext signatureContext = new SignatureContext(transaction, hashType, blockHeight);
        signatureContext.setInputIndexBeingSigned(transactionInputIndexBeingSigned);
        signatureContext.setShouldSignInputScript(transactionInputIndexBeingSigned, true, transactionOutputBeingSpent);
        signatureContext.setLastCodeSeparatorIndex(transactionInputIndexBeingSigned, codeSeparatorIndex);
        signatureContext.setCurrentScript(currentScript);
        signatureContext.setBytesToExcludeFromScript(bytesToExcludeFromScript);
        return transactionSigner.isSignatureValid(signatureContext, publicKey, scriptSignature);
    }

    protected static Boolean validateStrictSignatureEncoding(final ScriptSignature scriptSignature, final ScriptSignatureContext scriptSignatureContext, final Context context) {
        if (scriptSignature == null) { return false; }
        if (scriptSignature.isEmpty()) { return true; } // "If a signature passing to ECDSA verification does not pass the Low S value check and is not an empty byte array, the entire script evaluates to false immediately."

        if (scriptSignatureContext == ScriptSignatureContext.CHECK_SIGNATURE) { // CheckDataSignatures do not contain a HashType...
            // Enforce SCRIPT_VERIFY_STRICTENC... (https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/uahf-technical-spec.md) (BitcoinXT: src/script/interpreter.cpp ::IsValidSignatureEncoding) (BitcoinXT: src/script/sigencoding.cpp ::IsValidSignatureEncoding)
            // https://github.com/bitcoin/bips/blob/master/bip-0146.mediawiki
            final HashType hashType = scriptSignature.getHashType();
            if (hashType == null) { return false; }

            if (hashType.getMode() == null) { return false; }

            if (REQUIRE_BITCOIN_CASH_FORK_ID) {
                if (! hashType.isBitcoinCashType()) { return false; }
            }
        }

        {
            final Signature signature = scriptSignature.getSignature();
            if (signature != null) {
                // Check for negative-encoded DER signatures...
                if (signature.getType() == Signature.Type.SECP256K1) { // Bip66 only applies to DER encoded signatures...
                    if (Bip66.isEnabled(context.getBlockHeight())) { // Enforce non-negative R and S encoding for DER encoded signatures...
                        final ByteArray signatureR = signature.getR();
                        if (signatureR.getByteCount() == 0) { return false; }
                        if ((signatureR.getByte(0) & 0x80) != 0) { return false; }

                        final ByteArray signatureS = signature.getS();
                        if (signatureS.getByteCount() == 0) { return false; }
                        if ((signatureS.getByte(0) & 0x80) != 0) { return false; }
                    }
                }

                // Enforce LOW_S Signature Encoding... (https://github.com/bitcoin/bips/blob/master/bip-0146.mediawiki#low_s)
                final Boolean isCanonical = signature.isCanonical();
                if (! isCanonical) { return false; }
            }
        }

        return true;
    }

    protected static Boolean validateStrictPublicKeyEncoding(final PublicKey publicKey) {
        return ( publicKey.isCompressed() || publicKey.isDecompressed() );
    }

    protected Boolean _executeCheckSignature(final Stack stack, final Context context) {
        final ScriptSignatureContext scriptSignatureContext = ScriptSignatureContext.CHECK_SIGNATURE;

        final Value publicKeyValue = stack.pop();
        final Value signatureValue = stack.pop();

        if (stack.didOverflow()) { return false; }

        final List<ByteArray> bytesToRemoveFromScript;
        { // NOTE: All instances of the signature should be purged from the signed script...
            final ImmutableListBuilder<ByteArray> signatureBytesBuilder = new ImmutableListBuilder<ByteArray>(1);
            signatureBytesBuilder.add(MutableByteArray.wrap(signatureValue.getBytes()));
            bytesToRemoveFromScript = signatureBytesBuilder.build();
        }

        final Long blockHeight = context.getBlockHeight();

        final Boolean signatureIsValid;
        {
            final ScriptSignature scriptSignature = signatureValue.asScriptSignature(scriptSignatureContext);

            if (Buip55.isEnabled(blockHeight)) { // Enforce strict signature encoding (SCRIPT_VERIFY_STRICTENC)...
                final Boolean signatureIsStrictlyEncoded = CryptographicOperation.validateStrictSignatureEncoding(scriptSignature, scriptSignatureContext, context);
                if (! signatureIsStrictlyEncoded) { return false; }
            }

            if ( (scriptSignature != null) && (! scriptSignature.isEmpty()) ) {
                final PublicKey publicKey = publicKeyValue.asPublicKey();

                if (Buip55.isEnabled(blockHeight)) { // Enforce strict signature encoding (SCRIPT_VERIFY_STRICTENC)...
                    final Boolean publicKeyIsStrictlyEncoded = CryptographicOperation.validateStrictPublicKeyEncoding(publicKey);
                    if (! publicKeyIsStrictlyEncoded) { return false; }
                }

                signatureIsValid = CryptographicOperation.verifySignature(context, publicKey, scriptSignature, bytesToRemoveFromScript);
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
            if (FAIL_ON_BAD_SIGNATURE_ENABLED) { // Enforce NULLFAIL... (https://github.com/bitcoin/bips/blob/master/bip-0146.mediawiki#nullfail)
                if (HF20171113.isEnabled(blockHeight)) {
                    if ((! signatureIsValid) && (! signatureValue.isEmpty())) { return false; }
                }
            }

            stack.push(Value.fromBoolean(signatureIsValid));
        }

        return (! stack.didOverflow());
    }

    protected Boolean _executeCheckMultiSignature(final Stack stack, final Context context) {
        final ScriptSignatureContext scriptSignatureContext = ScriptSignatureContext.CHECK_SIGNATURE;

        final Integer publicKeyCount;
        {
            final Value publicKeyCountValue = stack.pop();
            publicKeyCount = publicKeyCountValue.asLong().intValue();
            if (publicKeyCount < 0) { return false; }
            if (publicKeyCount > MAX_MULTI_SIGNATURE_PUBLIC_KEY_COUNT) { return false; }
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
            signatureCount = signatureCountValue.asLong().intValue();
            if (signatureCount < 0) { return false; }
            if (signatureCount > publicKeyCount) { return false; }
        }

        final boolean allSignaturesWereEmpty;
        final List<ByteArray> bytesToRemoveFromScript;
        final List<ScriptSignature> signatures;
        {
            boolean signaturesAreEmpty = true;
            final ImmutableListBuilder<ByteArray> signatureBytesBuilder = new ImmutableListBuilder<ByteArray>(signatureCount);
            final ImmutableListBuilder<ScriptSignature> listBuilder = new ImmutableListBuilder<ScriptSignature>(signatureCount);
            for (int i = 0; i < signatureCount; ++i) {
                final Value signatureValue = stack.pop();

                if (! signatureValue.isEmpty()) {
                    signaturesAreEmpty = false;
                }

                final ScriptSignature scriptSignature = signatureValue.asScriptSignature(scriptSignatureContext);

                if ( (scriptSignature != null) && (! scriptSignature.isEmpty()) ) {
                    // Schnorr signatures are currently disabled for OP_CHECKMULTISIG...
                    final Signature signature = scriptSignature.getSignature();
                    if (signature.getType() == Signature.Type.SCHNORR) {
                        return false;
                    }
                }

                signatureBytesBuilder.add(MutableByteArray.wrap(signatureValue.getBytes())); // NOTE: All instances of the signature should be purged from the signed script...
                listBuilder.add(scriptSignature);
            }
            signatures = listBuilder.build();
            bytesToRemoveFromScript = signatureBytesBuilder.build();
            allSignaturesWereEmpty = signaturesAreEmpty;
        }

        stack.pop(); // Pop an extra value due to bug in the protocol...

        final Long blockHeight = context.getBlockHeight();

        final boolean signaturesAreValid;
        {   // Signatures must appear in the same order as their paired public key, but the number of signatures may be less than the number of public keys.
            // Example: P1, P2, P3 <-> S2, S3
            //          P1, P2, P3 <-> S1, S3
            //          P1, P2, P3 <-> S1, S2
            //          P1, P2, P3 <-> S1, S2, S3
            boolean signaturesHaveMatchedPublicKeys = true;
            int nextPublicKeyIndex = 0;
            for (int i = 0; i < signatureCount; ++i) {
                final ScriptSignature scriptSignature = signatures.get(i);

                boolean signatureHasPublicKeyMatch = false;
                for (int j = nextPublicKeyIndex; j < publicKeyCount; ++j) {
                    nextPublicKeyIndex += 1;

                    final PublicKey publicKey = publicKeys.get(j);

                    // Signatures and PublicKeys that are not used are allowed to be coded incorrectly.
                    //  Therefore, the publicKey checking is performed immediately before the signature check, and not when popped from the stack.
                    if (Buip55.isEnabled(context.getBlockHeight())) { // Enforce strict signature encoding (SCRIPT_VERIFY_STRICTENC)...
                        final Boolean publicKeyIsStrictlyEncoded = CryptographicOperation.validateStrictPublicKeyEncoding(publicKey);
                        if (! publicKeyIsStrictlyEncoded) { return false; }

                        final Boolean meetsStrictEncodingStandard = CryptographicOperation.validateStrictSignatureEncoding(scriptSignature, scriptSignatureContext, context);
                        if (! meetsStrictEncodingStandard) { return false; }
                    }

                    final boolean signatureIsValid;
                    {
                        if ( (scriptSignature != null) && (! scriptSignature.isEmpty()) ) {
                            signatureIsValid = CryptographicOperation.verifySignature(context, publicKey, scriptSignature, bytesToRemoveFromScript);
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
            if (FAIL_ON_BAD_SIGNATURE_ENABLED) { // Enforce NULLFAIL... (https://github.com/bitcoin/bips/blob/master/bip-0146.mediawiki#nullfail)
                if (HF20171113.isEnabled(blockHeight)) {
                    if ((! signaturesAreValid) && (! allSignaturesWereEmpty)) { return false; }
                }
            }

            stack.push(Value.fromBoolean(signaturesAreValid));
            // stack.push(Value.fromBoolean(true));
        }

        return (! stack.didOverflow());
    }

    protected Boolean _executeCheckDataSignature(final Stack stack, final Context context) {
        final ScriptSignatureContext scriptSignatureContext = ScriptSignatureContext.CHECK_DATA_SIGNATURE;

        final Value publicKeyValue = stack.pop();
        final Value messageValue = stack.pop();
        final Value signatureValue = stack.pop();

        final ScriptSignature scriptSignature = signatureValue.asScriptSignature(scriptSignatureContext);
        if (Buip55.isEnabled(context.getBlockHeight())) { // Enforce strict signature encoding (SCRIPT_VERIFY_STRICTENC)...
            final Boolean meetsStrictEncodingStandard = CryptographicOperation.validateStrictSignatureEncoding(scriptSignature, scriptSignatureContext, context);
            if (! meetsStrictEncodingStandard) { return false; }
        }

        final byte[] messageHash = BitcoinUtil.sha256(messageValue.getBytes());

        final Boolean signatureIsValid;
        if ( (scriptSignature != null) && (! scriptSignature.isEmpty()) ) {
            final PublicKey publicKey = publicKeyValue.asPublicKey();
            // if (publicKey == null) { return false; } // The PublicKey must be a valid for OP_CHECKDATASIG...

            if (Buip55.isEnabled(context.getBlockHeight())) { // Enforce strict signature encoding (SCRIPT_VERIFY_STRICTENC)...
                final Boolean publicKeyIsStrictlyEncoded = CryptographicOperation.validateStrictPublicKeyEncoding(publicKey);
                if (! publicKeyIsStrictlyEncoded) { return false; }
            }

            final Signature signature = scriptSignature.getSignature();

            if (signature.getType() == Signature.Type.SCHNORR) {
                signatureIsValid = Schnorr.verifySignature(signature, publicKey, messageHash);
            }
            else {
                signatureIsValid = Secp256k1.verifySignature(signature, publicKey, messageHash);
            }
        }
        else {
            signatureIsValid = false;
        }

        if (FAIL_ON_BAD_SIGNATURE_ENABLED) {
            if ( (! signatureIsValid) && (! signatureValue.isEmpty()) ) { return false; } // Enforce NULLFAIL...
        }

        if (_opcode == Opcode.CHECK_DATA_SIGNATURE_THEN_VERIFY) {
            if (! signatureIsValid) { return false; }
        }
        else {
            stack.push(Value.fromBoolean(signatureIsValid));
        }

        return (! stack.didOverflow());
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

            case CHECK_SIGNATURE:
            case CHECK_SIGNATURE_THEN_VERIFY:{
                return _executeCheckSignature(stack, context);
            }

            case CHECK_MULTISIGNATURE:
            case CHECK_MULTISIGNATURE_THEN_VERIFY: {
                return _executeCheckMultiSignature(stack, context);
            }

            case CHECK_DATA_SIGNATURE:
            case CHECK_DATA_SIGNATURE_THEN_VERIFY: {
                if (! HF20181115.isEnabled(context.getBlockHeight())) { return false; }

                return _executeCheckDataSignature(stack, context);
            }

            default: { return false; }
        }
    }
}
