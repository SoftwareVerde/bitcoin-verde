package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.reader.ScriptReader;
import com.softwareverde.bitcoin.transaction.script.runner.Context;
import com.softwareverde.bitcoin.transaction.script.stack.ScriptSignature;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.transaction.signer.SignatureContext;
import com.softwareverde.bitcoin.transaction.signer.TransactionSigner;
import com.softwareverde.bitcoin.util.BitcoinUtil;

public class CryptographicOperation extends Operation {
    public static final Type TYPE = Type.OP_CRYPTOGRAPHIC;

    public static CryptographicOperation fromScript(final ScriptReader script) {
        if (! script.hasNextByte()) { return null; }

        final byte opcodeByte = script.getNextByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final SubType subType = TYPE.getSubtype(opcodeByte);
        if (subType == null) { return null; }

        return new CryptographicOperation(opcodeByte, subType);
    }

    protected final SubType _subType;

    protected CryptographicOperation(final byte value, final SubType subType) {
        super(value, TYPE);
        _subType = subType;
    }

    @Override
    public Boolean applyTo(final Stack stack, final Context context) {
        switch (_subType) {
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

            }

            case CHECK_SIGNATURE_THEN_VERIFY:
            case CHECK_SIGNATURE: {
                final Value publicKeyValue = stack.pop();
                final Value signatureValue = stack.pop();

                if (stack.didOverflow()) { return false; }

                final ScriptSignature scriptSignature = signatureValue.asScriptSignature();
                if (scriptSignature == null) { return false; }

                final Transaction transaction = context.getTransaction();
                final Integer transactionInputIndexBeingSigned = context.getTransactionInputIndex();
                final TransactionOutput transactionOutputBeingSpent = context.getTransactionOutput();

                final TransactionSigner transactionSigner = new TransactionSigner();
                final SignatureContext signatureContext = new SignatureContext(transaction, transactionInputIndexBeingSigned, transactionOutputBeingSpent);
                final Boolean signatureIsValid = transactionSigner.isSignatureValid(signatureContext, publicKeyValue.getBytes(), scriptSignature);

                if (_subType == SubType.CHECK_SIGNATURE_THEN_VERIFY) {
                    if (! signatureIsValid) { return false; }
                }

                stack.push(Value.fromBoolean(signatureIsValid));
                return (! stack.didOverflow());
            }

            case CHECK_MULTISIGNATURE: {

            }

            case CHECK_MULTISIGNATURE_THEN_VERIFY: {

            }

            default: { return false; }
        }
    }
}
