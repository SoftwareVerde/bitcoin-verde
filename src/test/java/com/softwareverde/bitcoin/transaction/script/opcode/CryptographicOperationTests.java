package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputInflater;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

public class CryptographicOperationTests {
    /*
    @Test
    public void should_verify_multisig_transaction_EB3B82C0884E3EFA6D8B0BE55B4915EB20BE124C9766245BCC7F34FDAC32BCCB_1() {
        // Setup
        final Stack stack = new Stack();
        {
            stack.push(Value.fromBytes(HexUtil.hexStringToByteArray("00000000")));
            stack.push(Value.fromBytes(HexUtil.hexStringToByteArray("30440220276D6DAD3DEFA37B5F81ADD3992D510D2F44A317FD85E04F93A1E2DAEA64660202200F862A0DA684249322CEB8ED842FB8C859C0CB94C81E1C5308B4868157A428EE01")));
            stack.push(Value.fromBytes(HexUtil.hexStringToByteArray("01000000")));
            stack.push(Value.fromBytes(HexUtil.hexStringToByteArray("0232ABDC893E7F0631364D7FD01CB33D24DA45329A00357B3A7886211AB414D55A")));
            stack.push(Value.fromBytes(HexUtil.hexStringToByteArray("01000000")));
        }

        final MutableContext mutableContext = new MutableContext();
        {
            final TransactionInflater transactionInflater = new TransactionInflater();
            final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("01000000024DE8B0C4C2582DB95FA6B3567A989B664484C7AD6672C85A3DA413773E63FDB8000000006B48304502205B282FBC9B064F3BC823A23EDCC0048CBB174754E7AA742E3C9F483EBE02911C022100E4B0B3A117D36CAB5A67404DDDBF43DB7BEA3C1530E0FE128EBC15621BD69A3B0121035AA98D5F77CD9A2D88710E6FC66212AFF820026F0DAD8F32D1F7CE87457DDE50FFFFFFFF4DE8B0C4C2582DB95FA6B3567A989B664484C7AD6672C85A3DA413773E63FDB8010000006F004730440220276D6DAD3DEFA37B5F81ADD3992D510D2F44A317FD85E04F93A1E2DAEA64660202200F862A0DA684249322CEB8ED842FB8C859C0CB94C81E1C5308B4868157A428EE01AB51210232ABDC893E7F0631364D7FD01CB33D24DA45329A00357B3A7886211AB414D55A51AEFFFFFFFF02E0FD1C00000000001976A914380CB3C594DE4E7E9B8E18DB182987BEBB5A4F7088ACC0C62D000000000017142A9BC5447D664C1D0141392A842D23DBA45C4F13B17500000000"));
            final Integer transactionInputIndex = 1;

            Logger.log(transaction.getHash());

            final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
            final Integer txOutIndex = 1;
            // final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(txOutIndex, HexUtil.hexStringToByteArray("C0C62D000000000017142A9BC5447D664C1D0141392A842D23DBA45C4F13B175"));
            final TransactionOutput transactionOutput;
            {
                final TransactionOutput txOutput = transactionOutputInflater.fromBytes(txOutIndex, HexUtil.hexStringToByteArray("C0C62D000000000017142A9BC5447D664C1D0141392A842D23DBA45C4F13B175"));
                final MutableTransactionOutput mutableTransactionOutput = new MutableTransactionOutput(txOutput);
                final MutableScript mutableScript = new MutableScript();
                mutableScript.addOperation(txOutput.getLockingScript().getOperations().get(0));
                mutableTransactionOutput.setLockingScript(LockingScript.castFrom(mutableScript));
                transactionOutput = mutableTransactionOutput;
            }
            final Integer codeSeparatorIndex = 0; // 3;

            mutableContext.setTransaction(transaction);
            mutableContext.setTransactionInputIndex(transactionInputIndex);
            mutableContext.setTransactionOutput(transactionOutput);
            mutableContext.setLockingScriptLastCodeSeparatorIndex(codeSeparatorIndex);
        }

        final CryptographicOperation checkMultisigOperation = new CryptographicOperation((byte) 0xAE, Operation.Opcode.CHECK_MULTISIGNATURE);

        // Action
        final Boolean shouldContinue = checkMultisigOperation.applyTo(stack, mutableContext);
        final Value lastValue = stack.pop();

        // Assert
        Assert.assertTrue(shouldContinue);
        Assert.assertFalse(stack.didOverflow());
        Assert.assertTrue(lastValue.asBoolean());
    }
    */

    /*
    @Test
    public void should_verify_multisig_transaction_EB3B82C0884E3EFA6D8B0BE55B4915EB20BE124C9766245BCC7F34FDAC32BCCB_1() {
        // Setup
        final Stack stack = new Stack();
        {
            stack.push(Value.fromBytes(HexUtil.hexStringToByteArray("00000000")));
            stack.push(Value.fromBytes(HexUtil.hexStringToByteArray("30440220276D6DAD3DEFA37B5F81ADD3992D510D2F44A317FD85E04F93A1E2DAEA64660202200F862A0DA684249322CEB8ED842FB8C859C0CB94C81E1C5308B4868157A428EE01")));
            stack.push(Value.fromBytes(HexUtil.hexStringToByteArray("01000000")));
            stack.push(Value.fromBytes(HexUtil.hexStringToByteArray("0232ABDC893E7F0631364D7FD01CB33D24DA45329A00357B3A7886211AB414D55A")));
            stack.push(Value.fromBytes(HexUtil.hexStringToByteArray("01000000")));
        }

        final MutableContext mutableContext = new MutableContext();
        {
            final TransactionInflater transactionInflater = new TransactionInflater();
            final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("01000000024DE8B0C4C2582DB95FA6B3567A989B664484C7AD6672C85A3DA413773E63FDB8000000006B48304502205B282FBC9B064F3BC823A23EDCC0048CBB174754E7AA742E3C9F483EBE02911C022100E4B0B3A117D36CAB5A67404DDDBF43DB7BEA3C1530E0FE128EBC15621BD69A3B0121035AA98D5F77CD9A2D88710E6FC66212AFF820026F0DAD8F32D1F7CE87457DDE50FFFFFFFF4DE8B0C4C2582DB95FA6B3567A989B664484C7AD6672C85A3DA413773E63FDB8010000006F004730440220276D6DAD3DEFA37B5F81ADD3992D510D2F44A317FD85E04F93A1E2DAEA64660202200F862A0DA684249322CEB8ED842FB8C859C0CB94C81E1C5308B4868157A428EE01AB51210232ABDC893E7F0631364D7FD01CB33D24DA45329A00357B3A7886211AB414D55A51AEFFFFFFFF02E0FD1C00000000001976A914380CB3C594DE4E7E9B8E18DB182987BEBB5A4F7088ACC0C62D000000000017142A9BC5447D664C1D0141392A842D23DBA45C4F13B17500000000"));
            final Integer transactionInputIndex = 1;

            Logger.log(transaction.getHash());

            final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
            final Integer txOutIndex = 1;
            final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(txOutIndex, HexUtil.hexStringToByteArray("C0C62D000000000017142A9BC5447D664C1D0141392A842D23DBA45C4F13B175"));
            final Integer codeSeparatorIndex = 0; // 3;

            mutableContext.setTransaction(transaction);
            mutableContext.setTransactionInputIndex(transactionInputIndex);
            mutableContext.setTransactionOutput(transactionOutput);
            mutableContext.setLockingScriptLastCodeSeparatorIndex(codeSeparatorIndex);
        }

        final CryptographicOperation checkMultisigOperation = new CryptographicOperation((byte) 0xAE, Operation.Opcode.CHECK_MULTISIGNATURE);

        // Action
        final Boolean shouldContinue = checkMultisigOperation.applyTo(stack, mutableContext);
        final Value lastValue = stack.pop();

        // Assert
        Assert.assertTrue(shouldContinue);
        Assert.assertFalse(stack.didOverflow());
        Assert.assertTrue(lastValue.asBoolean());
    }
    */

    @Test
    public void should_verify_multisig_transaction_EB3B82C0884E3EFA6D8B0BE55B4915EB20BE124C9766245BCC7F34FDAC32BCCB_1() {
        // Setup
        final Stack stack = new Stack();
        {
            stack.push(Value.fromBytes(HexUtil.hexStringToByteArray("00000000")));
            stack.push(Value.fromBytes(HexUtil.hexStringToByteArray("30440220276D6DAD3DEFA37B5F81ADD3992D510D2F44A317FD85E04F93A1E2DAEA64660202200F862A0DA684249322CEB8ED842FB8C859C0CB94C81E1C5308B4868157A428EE01")));
            stack.push(Value.fromBytes(HexUtil.hexStringToByteArray("01000000")));
            stack.push(Value.fromBytes(HexUtil.hexStringToByteArray("0232ABDC893E7F0631364D7FD01CB33D24DA45329A00357B3A7886211AB414D55A")));
            stack.push(Value.fromBytes(HexUtil.hexStringToByteArray("01000000")));
        }

        final MutableContext mutableContext = new MutableContext();
        {
            final TransactionInflater transactionInflater = new TransactionInflater();
            final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("01000000024DE8B0C4C2582DB95FA6B3567A989B664484C7AD6672C85A3DA413773E63FDB8000000006B48304502205B282FBC9B064F3BC823A23EDCC0048CBB174754E7AA742E3C9F483EBE02911C022100E4B0B3A117D36CAB5A67404DDDBF43DB7BEA3C1530E0FE128EBC15621BD69A3B0121035AA98D5F77CD9A2D88710E6FC66212AFF820026F0DAD8F32D1F7CE87457DDE50FFFFFFFF4DE8B0C4C2582DB95FA6B3567A989B664484C7AD6672C85A3DA413773E63FDB8010000006F004730440220276D6DAD3DEFA37B5F81ADD3992D510D2F44A317FD85E04F93A1E2DAEA64660202200F862A0DA684249322CEB8ED842FB8C859C0CB94C81E1C5308B4868157A428EE01AB51210232ABDC893E7F0631364D7FD01CB33D24DA45329A00357B3A7886211AB414D55A51AEFFFFFFFF02E0FD1C00000000001976A914380CB3C594DE4E7E9B8E18DB182987BEBB5A4F7088ACC0C62D000000000017142A9BC5447D664C1D0141392A842D23DBA45C4F13B17500000000"));
            final Integer transactionInputIndex = 1;

            Logger.log(transaction.getHash());

            final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
            final Integer txOutIndex = 1;
            final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(txOutIndex, HexUtil.hexStringToByteArray("C0C62D000000000017142A9BC5447D664C1D0141392A842D23DBA45C4F13B175"));
            final Integer codeSeparatorIndex = 3;

            mutableContext.setTransaction(transaction);
            mutableContext.setTransactionInputIndex(transactionInputIndex);
            mutableContext.setTransactionOutput(transactionOutput);
            mutableContext.setLockingScriptLastCodeSeparatorIndex(codeSeparatorIndex);
        }

        final CryptographicOperation checkMultisigOperation = new CryptographicOperation((byte) 0xAE, Operation.Opcode.CHECK_MULTISIGNATURE);

        // Action
        final Boolean shouldContinue = checkMultisigOperation.applyTo(stack, mutableContext);
        final Value lastValue = stack.pop();

        // Assert
        Assert.assertTrue(shouldContinue);
        Assert.assertFalse(stack.didOverflow());
        Assert.assertTrue(lastValue.asBoolean());
    }


    /*
        @Test
        public void should_verify_multisig_transaction_EB3B82C0884E3EFA6D8B0BE55B4915EB20BE124C9766245BCC7F34FDAC32BCCB_1() {
            // Setup
            final Stack stack = new Stack();
            {
                stack.push(Value.fromBytes(HexUtil.hexStringToByteArray("00000000")));
                stack.push(Value.fromBytes(HexUtil.hexStringToByteArray("30440220276D6DAD3DEFA37B5F81ADD3992D510D2F44A317FD85E04F93A1E2DAEA64660202200F862A0DA684249322CEB8ED842FB8C859C0CB94C81E1C5308B4868157A428EE01")));
                stack.push(Value.fromBytes(HexUtil.hexStringToByteArray("01000000")));
                stack.push(Value.fromBytes(HexUtil.hexStringToByteArray("0232ABDC893E7F0631364D7FD01CB33D24DA45329A00357B3A7886211AB414D55A")));
                stack.push(Value.fromBytes(HexUtil.hexStringToByteArray("01000000")));
            }

            final MutableContext mutableContext = new MutableContext();
            {
                final TransactionInflater transactionInflater = new TransactionInflater();
                final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("01000000024DE8B0C4C2582DB95FA6B3567A989B664484C7AD6672C85A3DA413773E63FDB8000000006B48304502205B282FBC9B064F3BC823A23EDCC0048CBB174754E7AA742E3C9F483EBE02911C022100E4B0B3A117D36CAB5A67404DDDBF43DB7BEA3C1530E0FE128EBC15621BD69A3B0121035AA98D5F77CD9A2D88710E6FC66212AFF820026F0DAD8F32D1F7CE87457DDE50FFFFFFFF4DE8B0C4C2582DB95FA6B3567A989B664484C7AD6672C85A3DA413773E63FDB8010000006F004730440220276D6DAD3DEFA37B5F81ADD3992D510D2F44A317FD85E04F93A1E2DAEA64660202200F862A0DA684249322CEB8ED842FB8C859C0CB94C81E1C5308B4868157A428EE01AB51210232ABDC893E7F0631364D7FD01CB33D24DA45329A00357B3A7886211AB414D55A51AEFFFFFFFF02E0FD1C00000000001976A914380CB3C594DE4E7E9B8E18DB182987BEBB5A4F7088ACC0C62D000000000017142A9BC5447D664C1D0141392A842D23DBA45C4F13B17500000000"));
                final Integer transactionInputIndex = 1;

                Logger.log(transaction.getHash());

                final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
                final Integer txOutIndex = 1;
                final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(txOutIndex, HexUtil.hexStringToByteArray("C0C62D000000000017142A9BC5447D664C1D0141392A842D23DBA45C4F13B175"));
                final Integer codeSeparatorIndex = 0; // 3;

                mutableContext.setTransaction(transaction);
                mutableContext.setTransactionInputIndex(transactionInputIndex);
                mutableContext.setTransactionOutput(transactionOutput);
                mutableContext.setLockingScriptLastCodeSeparatorIndex(codeSeparatorIndex);
            }

            final CryptographicOperation checkMultisigOperation = new CryptographicOperation((byte) 0xAE, Operation.Opcode.CHECK_MULTISIGNATURE);

            // Action
            final Boolean shouldContinue = checkMultisigOperation.applyTo(stack, mutableContext);
            final Value lastValue = stack.pop();

            // Assert
            Assert.assertTrue(shouldContinue);
            Assert.assertFalse(stack.didOverflow());
            Assert.assertTrue(lastValue.asBoolean());
        }
    */

}
