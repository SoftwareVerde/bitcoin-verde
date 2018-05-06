package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInputInflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputInflater;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.locking.ImmutableLockingScript;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.opcode.ControlState;
import com.softwareverde.bitcoin.transaction.script.opcode.CryptographicOperation;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.OperationInflater;
import com.softwareverde.bitcoin.transaction.script.runner.ScriptRunner;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.transaction.script.unlocking.ImmutableUnlockingScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.list.List;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class HistoricTransactionsTests {
    @After
    public void shutdown() {
        Logger.shutdown();
    }

    @Test
    public void should_verify_multisig_transaction_EB3B82C0884E3EFA6D8B0BE55B4915EB20BE124C9766245BCC7F34FDAC32BCCB_1() throws Exception {
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

            final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
            final Integer txOutIndex = 1;
            final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(txOutIndex, HexUtil.hexStringToByteArray("C0C62D000000000017142A9BC5447D664C1D0141392A842D23DBA45C4F13B175"));
            final Integer codeSeparatorIndex = 3;

            final Script unlockingScript;
            {
                final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
                final TransactionInput transactionInput = transactionInputs.get(transactionInputIndex);
                unlockingScript = transactionInput.getUnlockingScript();
            }

            mutableContext.setCurrentScript(unlockingScript); // Set the current script, since ScriptRunner isn't being used here...
            mutableContext.setTransaction(transaction);
            mutableContext.setTransactionInputIndex(transactionInputIndex);
            mutableContext.setTransactionOutput(transactionOutput);
            mutableContext.setLockingScriptLastCodeSeparatorIndex(codeSeparatorIndex);
        }

        final ControlState controlState = new ControlState();

        // final CryptographicOperation checkMultisigOperation = new CryptographicOperation((byte) 0xAE, Operation.Opcode.CHECK_MULTISIGNATURE);
        final OperationInflater operationInflater = new OperationInflater();
        final Operation checkMultisigOperation = operationInflater.fromBytes(new ByteArrayReader(new byte[] { (byte) 0xAE }));
        Assert.assertTrue(checkMultisigOperation instanceof CryptographicOperation);

        // Action
        final Boolean shouldContinue = checkMultisigOperation.applyTo(stack, controlState, mutableContext);
        final Value lastValue = stack.pop();

        // Assert
        Assert.assertTrue(shouldContinue);
        Assert.assertFalse(stack.didOverflow());
        Assert.assertTrue(lastValue.asBoolean());
    }

    @Test
    public void should_verify_SIGHASHNONE_transaction_599E47A8114FE098103663029548811D2651991B62397E057F0C863C2BC9F9EA_1() {
        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("01000000015F386C8A3842C9A9DCFA9B78BE785A40A7BDA08B64646BE3654301EACCFC8D5E010000008A4730440220BB4FBC495AA23BABB2C2BE4E3FB4A5DFFEFE20C8EFF5940F135649C3EA96444A022004AFCDA966C807BB97622D3EEFEA828F623AF306EF2B756782EE6F8A22A959A2024104F1939AE6B01E849BF05D0ED51FD5B92B79A0E313E3F389C726F11FA3E144D9227B07E8A87C0EE36372E967E090D11B777707AA73EFACABFFFFA285C00B3622D6FFFFFFFF0240420F00000000001976A914660D4EF3A743E3E696AD990364E555C271AD504B88AC2072C801000000001976A91421C43CE400901312A603E4207AADFD742BE8E7DA88AC00000000"));

        final TransactionInputInflater transactionInputInflater = new TransactionInputInflater();
        final TransactionInput transactionInput = transactionInputInflater.fromBytes(HexUtil.hexStringToByteArray("5F386C8A3842C9A9DCFA9B78BE785A40A7BDA08B64646BE3654301EACCFC8D5E010000008A4730440220BB4FBC495AA23BABB2C2BE4E3FB4A5DFFEFE20C8EFF5940F135649C3EA96444A022004AFCDA966C807BB97622D3EEFEA828F623AF306EF2B756782EE6F8A22A959A2024104F1939AE6B01E849BF05D0ED51FD5B92B79A0E313E3F389C726F11FA3E144D9227B07E8A87C0EE36372E967E090D11B777707AA73EFACABFFFFA285C00B3622D6FFFFFFFF"));

        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(1, HexUtil.hexStringToByteArray("60B4D701000000001976A91421C43CE400901312A603E4207AADFD742BE8E7DA88AC"));

        final MutableContext context = new MutableContext();
        {
            context.setBlockHeight(178627L);
            context.setTransaction(transaction);

            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(transactionOutput);
            context.setTransactionInputIndex(0);
        }

        final LockingScript lockingScript = new ImmutableLockingScript(HexUtil.hexStringToByteArray("76A91421C43CE400901312A603E4207AADFD742BE8E7DA88AC"));
        final UnlockingScript unlockingScript = new ImmutableUnlockingScript(HexUtil.hexStringToByteArray("4730440220BB4FBC495AA23BABB2C2BE4E3FB4A5DFFEFE20C8EFF5940F135649C3EA96444A022004AFCDA966C807BB97622D3EEFEA828F623AF306EF2B756782EE6F8A22A959A2024104F1939AE6B01E849BF05D0ED51FD5B92B79A0E313E3F389C726F11FA3E144D9227B07E8A87C0EE36372E967E090D11B777707AA73EFACABFFFFA285C00B3622D6"));

        final ScriptRunner scriptRunner = new ScriptRunner();

        // Action
        final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);

        // Assert
        Assert.assertTrue(inputIsUnlocked);
    }

    @Test
    public void should_verify_pay_to_script_hash_transaction_6A26D2ECB67F27D1FA5524763B49029D7106E91E3CC05743073461A719776192_1() {
        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("0100000001F6EA284EC7521F8A7D094A6CF4E6873098B90F90725FFD372B343189D7A4089C0100000026255121029B6D2C97B8B7C718C325D7BE3AC30F7C9D67651BCE0C929F55EE77CE58EFCF8451AEFFFFFFFF0130570500000000001976A9145A3ACBC7BBCC97C5FF16F5909C9D7D3FADB293A888AC00000000"));

        final TransactionInputInflater transactionInputInflater = new TransactionInputInflater();
        final TransactionInput transactionInput = transactionInputInflater.fromBytes(HexUtil.hexStringToByteArray("F6EA284EC7521F8A7D094A6CF4E6873098B90F90725FFD372B343189D7A4089C0100000026255121029B6D2C97B8B7C718C325D7BE3AC30F7C9D67651BCE0C929F55EE77CE58EFCF8451AEFFFFFFFF"));

        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(1, HexUtil.hexStringToByteArray("801A06000000000017A91419A7D869032368FD1F1E26E5E73A4AD0E474960E87"));

        final MutableContext context = new MutableContext();
        {
            context.setBlockHeight(170095L); // NOTE: P2SH was not activated until Block 173805...
            context.setTransaction(transaction);

            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(transactionOutput);
            context.setTransactionInputIndex(0);
        }

        final LockingScript lockingScript = new ImmutableLockingScript(HexUtil.hexStringToByteArray("A91419A7D869032368FD1F1E26E5E73A4AD0E474960E87"));
        final UnlockingScript unlockingScript = new ImmutableUnlockingScript(HexUtil.hexStringToByteArray("255121029B6D2C97B8B7C718C325D7BE3AC30F7C9D67651BCE0C929F55EE77CE58EFCF8451AE"));

        final ScriptRunner scriptRunner = new ScriptRunner();

        // Action
        final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);

        // Assert
        Assert.assertTrue(inputIsUnlocked);
    }

    @Test
    public void should_verify_pay_to_script_hash_transaction_1CC1ECDF5C05765DF3D1F59FBA24CD01C45464C329B0F0A25AA9883ADFCF7F29_1() {
        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("01000000014ACC6214C74AF23AE430AFF165004D8E10DB9860B87FFB7E7E703AF97B872203010000009200483045022100BEB926DA7428FA009AC770576342EBD1960939E73584A5D0F3229B58C41E906F022017C0D143077906AFCCF30CAF21F5ECE0BB30E3F708FD4A17F9D9EF9FE7CDC983014751210307AC6296168948C3F64CE22F51F6E5424F936C846F1D01223B3D9864F4D955662103AC6AD514715BEC8D5DE1873B9BC873BB71773B51338B4D115F9938B6A029B7D152AEFFFFFFFF02C0175302000000001976A9146723D3398B384F0C0A8F717C100905F36E2ED7D488AC80969800000000001976A9148A033817801503863C9D6BD124D153F8407F2B4188AC00000000"));

        final TransactionInputInflater transactionInputInflater = new TransactionInputInflater();
        final TransactionInput transactionInput = transactionInputInflater.fromBytes(HexUtil.hexStringToByteArray("4ACC6214C74AF23AE430AFF165004D8E10DB9860B87FFB7E7E703AF97B872203010000009200483045022100BEB926DA7428FA009AC770576342EBD1960939E73584A5D0F3229B58C41E906F022017C0D143077906AFCCF30CAF21F5ECE0BB30E3F708FD4A17F9D9EF9FE7CDC983014751210307AC6296168948C3F64CE22F51F6E5424F936C846F1D01223B3D9864F4D955662103AC6AD514715BEC8D5DE1873B9BC873BB71773B51338B4D115F9938B6A029B7D152AEFFFFFFFF"));

        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(1, HexUtil.hexStringToByteArray("80F0FA020000000017A9145C02C49641699863F909BF4BF3BE8398D2E383F187"));

        final MutableContext context = new MutableContext();
        {
            context.setBlockHeight(177644L);
            context.setTransaction(transaction);

            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(transactionOutput);
            context.setTransactionInputIndex(0);
        }

        final LockingScript lockingScript = new ImmutableLockingScript(HexUtil.hexStringToByteArray("A9145C02C49641699863F909BF4BF3BE8398D2E383F187"));
        final UnlockingScript unlockingScript = new ImmutableUnlockingScript(HexUtil.hexStringToByteArray("00483045022100BEB926DA7428FA009AC770576342EBD1960939E73584A5D0F3229B58C41E906F022017C0D143077906AFCCF30CAF21F5ECE0BB30E3F708FD4A17F9D9EF9FE7CDC983014751210307AC6296168948C3F64CE22F51F6E5424F936C846F1D01223B3D9864F4D955662103AC6AD514715BEC8D5DE1873B9BC873BB71773B51338B4D115F9938B6A029B7D152AE"));

        final ScriptRunner scriptRunner = new ScriptRunner();

        // Action
        final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);

        // Assert
        Assert.assertTrue(inputIsUnlocked);
    }

    @Test
    public void should_verify_pay_to_script_hash_transaction_968A692AB98B1F275C635C76BE003AB1DB9740D0B62F338B270115342CA42F5B_1() {
        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("010000000120DA26875149D4F80F3C36ABC7F984720ED14F6396854AD1ABCA4FE72C88DC4F00000000FDFD0000483045022100939D7023833AAFFCB0DC2E6A0065316B90B7987BF3F4D99C5BC8811811782F34022064B5C3EF966E3312D615A06A3119D7599F12621BB92AC5CF1D971EF1AD9C8A65014730440220394151FB40EDD54326FA829EC571165A5E9168484293FD77E3713A35A701B43B02206B0FFF8488597790E2F6D62847CDE16DE3234439800F019423E7BEF881311862014C695221032C6AA78662CC43A3BB0F8F850D0C45E18D0A49C61EC69DB87E072C88D7A9B6E9210353581FD2FC745D17264AF8CB8CD507D82C9658962567218965E750590E41C41E21024FE45DD4749347D281FD5348F56E883EE3A00903AF899301AC47BA90F904854F53AEFFFFFFFF0330C11D00000000001976A914FD5E323C595B2614F47D6BE25BA079F081628C9B88AC80969800000000001976A91406F1B67078FC400A63D54C313CD6BB817E4760F088AC40787D01000000001976A9140231C76FF14600B49F7C1B734A69F169C7BA1BAC88AC00000000"));

        final TransactionInputInflater transactionInputInflater = new TransactionInputInflater();
        final TransactionInput transactionInput = transactionInputInflater.fromBytes(HexUtil.hexStringToByteArray("20DA26875149D4F80F3C36ABC7F984720ED14F6396854AD1ABCA4FE72C88DC4F00000000FDFD0000483045022100939D7023833AAFFCB0DC2E6A0065316B90B7987BF3F4D99C5BC8811811782F34022064B5C3EF966E3312D615A06A3119D7599F12621BB92AC5CF1D971EF1AD9C8A65014730440220394151FB40EDD54326FA829EC571165A5E9168484293FD77E3713A35A701B43B02206B0FFF8488597790E2F6D62847CDE16DE3234439800F019423E7BEF881311862014C695221032C6AA78662CC43A3BB0F8F850D0C45E18D0A49C61EC69DB87E072C88D7A9B6E9210353581FD2FC745D17264AF8CB8CD507D82C9658962567218965E750590E41C41E21024FE45DD4749347D281FD5348F56E883EE3A00903AF899301AC47BA90F904854F53AEFFFFFFFF"));

        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(1, HexUtil.hexStringToByteArray("409334020000000017A91493DD75558893D97C53005F6B63B9E4005401A93187"));

        final MutableContext context = new MutableContext();
        {
            context.setBlockHeight(177653L);
            context.setTransaction(transaction);

            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(transactionOutput);
            context.setTransactionInputIndex(0);
        }

        final LockingScript lockingScript = new ImmutableLockingScript(HexUtil.hexStringToByteArray("A91493DD75558893D97C53005F6B63B9E4005401A93187"));
        final UnlockingScript unlockingScript = new ImmutableUnlockingScript(HexUtil.hexStringToByteArray("00483045022100939D7023833AAFFCB0DC2E6A0065316B90B7987BF3F4D99C5BC8811811782F34022064B5C3EF966E3312D615A06A3119D7599F12621BB92AC5CF1D971EF1AD9C8A65014730440220394151FB40EDD54326FA829EC571165A5E9168484293FD77E3713A35A701B43B02206B0FFF8488597790E2F6D62847CDE16DE3234439800F019423E7BEF881311862014C695221032C6AA78662CC43A3BB0F8F850D0C45E18D0A49C61EC69DB87E072C88D7A9B6E9210353581FD2FC745D17264AF8CB8CD507D82C9658962567218965E750590E41C41E21024FE45DD4749347D281FD5348F56E883EE3A00903AF899301AC47BA90F904854F53AE"));

        final ScriptRunner scriptRunner = new ScriptRunner();

        // Action
        final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);

        // Assert
        Assert.assertTrue(inputIsUnlocked);
    }

    @Test
    public void should_verify_transaction_0ADF1D18C0620CF2AF88DF250402A3D0E5CD90BC081E5BFF0EEE0631A9B232C0_2() {
        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("01000000045930CB11E0447A833C4EB510494D0DB9A73B8489085002806B4938DAD91A4D4C010000008B483045022100C47CCD574FEA4677B942CC2A5FD49D44CB9827996A090D86BBFEC14286C158D3022067377B328848AF6718BE8ED4F7DA7841DEFAFE9CA013DC5ED4FCD7BD86F2698601410459ED4A6929F45F4C40ED221DE16ADEBA13AD9F35E47C69DC1081F7F3540539DE2C85CA8A5B2DB1995B18FB1F1D54EE1183775A5362AD6B85A74975671006253DFFFFFFFFB6A82EAA6E0A93E5233FCD34AF2375E42C72BA6711CDCD672819F99AF781A8A5010000008C493046022100CCC7758371F7B9E301183C28696FA721A2E3617D50A3761C6A19D94408C4E753022100D54835B7B08C38A3992A69EED47717AFDC24738B9A81FB311536EC7AD8712613014104E1E5C8ADCA296F2EBBDA9C19FFC7D31732787A75182FF8BEE7EA51C2CD0D20C1CB26E008E49BC9D937D42D15C9686184068DEB9668C8B39F1F6C5D5E3BEB10DEFFFFFFFFB2A2B35A2750E5203820BFE92166E317E1A797327AED713BEE90FD8E2159453F010000008A4730440220710CAA1EBACA07EE4C8D055F57E4967B3948CBD2DBCA0BA002DEC8CA36BEBB0002200032F44BEDB3ABA4AD0CCA5E302B77D6D279B8A9EE3F66D02D5D0EC1BFB89E3E014104C33859AF99F7223FBF819F941143AC90D5DBCCCCF00B2FFAA6A1709F848AEFEF355019643D5727A5FFCFA594F870FB36D64506E6EEEC69624FB1719871CA4247FFFFFFFFAE49869B9ED83890F573211552504753756718332556CD35D535D67B81075EB0010000008B4830450221009DFF4FD759A1ABDCAF713F12465967AF66721604EE75812D61FA73DC633B924702203463AF5D7F1A098F8D705CFFF6AD0864434D526EABF6841F39C6DDA5FECC3754014104EC698E34FBB29B78B49C4574D8EAC51F292E0F72790AD3BC6AF856874715D68FB449E8FD4B05C7313C412C3DBF6788D00005E35C4DDA5A0F832343077520D7B5FFFFFFFF0200E721A2040000001976A91424DD0E523B29A97C039C4F3D605E969E38AFA02488ACF2DDEA4C000000001976A914B9887A490A7CE1CE00610EBC50CB629C02B1C57488AC00000000"));

        final TransactionInputInflater transactionInputInflater = new TransactionInputInflater();
        final TransactionInput transactionInput = transactionInputInflater.fromBytes(HexUtil.hexStringToByteArray("4730440220710CAA1EBACA07EE4C8D055F57E4967B3948CBD2DBCA0BA002DEC8CA36BEBB0002200032F44BEDB3ABA4AD0CCA5E302B77D6D279B8A9EE3F66D02D5D0EC1BFB89E3E014104C33859AF99F7223FBF819F941143AC90D5DBCCCCF00B2FFAA6A1709F848AEFEF355019643D5727A5FFCFA594F870FB36D64506E6EEEC69624FB1719871CA4247"));

        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(1, HexUtil.hexStringToByteArray("18E94C0C000000001976A9141A49982E41F9B53E6FCDDEEE6EE542BDA4E424DC88AC"));

        final MutableContext context = new MutableContext();
        {
            context.setBlockHeight(169940L);
            context.setTransaction(transaction);

            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(transactionOutput);
            context.setTransactionInputIndex(2);
        }

        final LockingScript lockingScript = new ImmutableLockingScript(HexUtil.hexStringToByteArray("76A9141A49982E41F9B53E6FCDDEEE6EE542BDA4E424DC88AC"));
        final UnlockingScript unlockingScript = new ImmutableUnlockingScript(HexUtil.hexStringToByteArray("4730440220710CAA1EBACA07EE4C8D055F57E4967B3948CBD2DBCA0BA002DEC8CA36BEBB0002200032F44BEDB3ABA4AD0CCA5E302B77D6D279B8A9EE3F66D02D5D0EC1BFB89E3E014104C33859AF99F7223FBF819F941143AC90D5DBCCCCF00B2FFAA6A1709F848AEFEF355019643D5727A5FFCFA594F870FB36D64506E6EEEC69624FB1719871CA4247"));

        final ScriptRunner scriptRunner = new ScriptRunner();

        // Action
        final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);

        // Assert
        Assert.assertTrue(inputIsUnlocked);
    }

    @Test
    public void should_verify_transaction_51BF528ECF3C161E7C021224197DBE84F9A8564212F6207BAA014C01A1668E1E_0() {
        // NOTE: This transaction relies on SIGHASH_ANYONECANPAY...

        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("0100000002F6044C0AD485F633B41F97D0D793EB2837AE40F738FF6D5F50FDFD10528C1D76010000006B48304502205853C7F1395785BFABB03C57E962EB076FF24D8E4E573B04DB13B45ED3ED6EE20221009DC82AE43BE9D4B1FE2847754E1D36DAD48BA801817D485DC529AFC516C2DDB481210305584980367B321FAD7F1C1F4D5D723D0AC80C1D80C8BA12343965B48364537AFFFFFFFF9C6AF0DF6669BCDED19E317E25BEBC8C78E48DF8AE1FE02A7F030818E71ECD40010000006C4930460221008269C9D7BA0A7E730DD16F4082D29E3684FB7463BA064FD093AFC170AD6E0388022100BC6D76373916A3FF6EE41B2C752001FDA3C9E048BCFF0D81D05B39FF0F4217B2812103AAE303D825421545C5BC7CCD5AC87DD5ADD3BCC3A432BA7AA2F2661699F9F659FFFFFFFF01E0930400000000001976A9145C11F917883B927EEF77DC57707AEB853F6D389488AC00000000"));

        final TransactionInputInflater transactionInputInflater = new TransactionInputInflater();
        final TransactionInput transactionInput = transactionInputInflater.fromBytes(HexUtil.hexStringToByteArray("F6044C0AD485F633B41F97D0D793EB2837AE40F738FF6D5F50FDFD10528C1D76010000006B48304502205853C7F1395785BFABB03C57E962EB076FF24D8E4E573B04DB13B45ED3ED6EE20221009DC82AE43BE9D4B1FE2847754E1D36DAD48BA801817D485DC529AFC516C2DDB481210305584980367B321FAD7F1C1F4D5D723D0AC80C1D80C8BA12343965B48364537AFFFFFFFF"));

        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(1, HexUtil.hexStringToByteArray("400D0300000000001976A9148551E48A53DECD1CFC63079A4581BCCCFAD1A93C88AC"));

        final MutableContext context = new MutableContext();
        {
            context.setBlockHeight(207733L);
            context.setTransaction(transaction);

            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(transactionOutput);
            context.setTransactionInputIndex(0);
        }

        final LockingScript lockingScript = new ImmutableLockingScript(HexUtil.hexStringToByteArray("76A9148551E48A53DECD1CFC63079A4581BCCCFAD1A93C88AC"));
        final UnlockingScript unlockingScript = new ImmutableUnlockingScript(HexUtil.hexStringToByteArray("48304502205853C7F1395785BFABB03C57E962EB076FF24D8E4E573B04DB13B45ED3ED6EE20221009DC82AE43BE9D4B1FE2847754E1D36DAD48BA801817D485DC529AFC516C2DDB481210305584980367B321FAD7F1C1F4D5D723D0AC80C1D80C8BA12343965B48364537A"));

        final ScriptRunner scriptRunner = new ScriptRunner();

        // Action
        final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);

        // Assert
        Assert.assertTrue(inputIsUnlocked);
    }

    @Test
    public void should_verify_transaction_7208E5EDF525F04E705FB3390194E316205B8F995C8C9FCD8C6093ABE04FA27D_0() {
        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("0100000001774431E6CCCA53548400E2E8BB66865CA2FEEF7885F09A0199B5FDD0EEB7583E01000000DC004930460221008662E2BFCC4741D92531F566C5765D849E24B45C289607593584FBBA938D88CC022100D718E710D5991BDFBDC5B0D52AE8E4D9C1C1C719BCC348003C5E80524A04E16283483045022100FBDBEA6B614989B210D269DBF171746A9507BB3DAE292BDAF85848A7AA091ECA02205D23A46269A904C40076C76EADFDB8F98E7D0349C0BFE5915CCA3F8835A4A41683475221034758CEFCB75E16E4DFAFB32383B709FA632086EA5CA982712DE6ADD93060B17A2103FE96237629128A0AE8C3825AF8A4BE8FE3109B16F62AF19CEC0B1EB93B8717E252AEFFFFFFFF0280969800000000001976A914F164A82C9B3C5D217C83380792D56A6261F2D17688AC609DF200000000001976A9142AB55D985E552653C189B1530AAC817C0223CB4C88AC00000000"));

        final TransactionInputInflater transactionInputInflater = new TransactionInputInflater();
        final TransactionInput transactionInput = transactionInputInflater.fromBytes(HexUtil.hexStringToByteArray("774431E6CCCA53548400E2E8BB66865CA2FEEF7885F09A0199B5FDD0EEB7583E01000000DC004930460221008662E2BFCC4741D92531F566C5765D849E24B45C289607593584FBBA938D88CC022100D718E710D5991BDFBDC5B0D52AE8E4D9C1C1C719BCC348003C5E80524A04E16283483045022100FBDBEA6B614989B210D269DBF171746A9507BB3DAE292BDAF85848A7AA091ECA02205D23A46269A904C40076C76EADFDB8F98E7D0349C0BFE5915CCA3F8835A4A41683475221034758CEFCB75E16E4DFAFB32383B709FA632086EA5CA982712DE6ADD93060B17A2103FE96237629128A0AE8C3825AF8A4BE8FE3109B16F62AF19CEC0B1EB93B8717E252AEFFFFFFFF"));

        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(1, HexUtil.hexStringToByteArray("80BA8C010000000017A914D0C15A7D41500976056B3345F542D8C944077C8A87"));

        final MutableContext context = new MutableContext();
        {
            context.setBlockHeight(217657L);
            context.setTransaction(transaction);

            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(transactionOutput);
            context.setTransactionInputIndex(0);
        }

        final LockingScript lockingScript = new ImmutableLockingScript(HexUtil.hexStringToByteArray("A914D0C15A7D41500976056B3345F542D8C944077C8A87"));
        final UnlockingScript unlockingScript = new ImmutableUnlockingScript(HexUtil.hexStringToByteArray("004930460221008662E2BFCC4741D92531F566C5765D849E24B45C289607593584FBBA938D88CC022100D718E710D5991BDFBDC5B0D52AE8E4D9C1C1C719BCC348003C5E80524A04E16283483045022100FBDBEA6B614989B210D269DBF171746A9507BB3DAE292BDAF85848A7AA091ECA02205D23A46269A904C40076C76EADFDB8F98E7D0349C0BFE5915CCA3F8835A4A41683475221034758CEFCB75E16E4DFAFB32383B709FA632086EA5CA982712DE6ADD93060B17A2103FE96237629128A0AE8C3825AF8A4BE8FE3109B16F62AF19CEC0B1EB93B8717E252AE"));

        final ScriptRunner scriptRunner = new ScriptRunner();

        // Action
        final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);

        // Assert
        Assert.assertTrue(inputIsUnlocked);
    }

    @Test
    public void should_verify_transaction_AFD9C17F8913577EC3509520BD6E5D63E9C0FD2A5F70C787993B097BA6CA9FAE_0() {
        // NOTE: This transaction has multiple inputs using SIGHASH_SINGLE

        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("010000000370AC0A1AE588AAF284C308D67CA92C69A39E2DB81337E563BF40C59DA0A5CF63000000006A4730440220360D20BAFF382059040BA9BE98947FD678FB08AAB2BB0C172EFA996FD8ECE9B702201B4FB0DE67F015C90E7AC8A193AEAB486A1F587E0F54D0FB9552EF7F5CE6CAEC032103579CA2E6D107522F012CD00B52B9A65FB46F0C57B9B8B6E377C48F526A44741AFFFFFFFF7D815B6447E35FBEA097E00E028FB7DFBAD4F3F0987B4734676C84F3FCD0E804010000006B483045022100C714310BE1E3A9FF1C5F7CACC65C2D8E781FC3A88CEB063C6153BF950650802102200B2D0979C76E12BB480DA635F192CC8DC6F905380DD4AC1FF35A4F68F462FFFD032103579CA2E6D107522F012CD00B52B9A65FB46F0C57B9B8B6E377C48F526A44741AFFFFFFFF3F1F097333E4D46D51F5E77B53264DB8F7F5D2E18217E1099957D0F5AF7713EE010000006C493046022100B663499EF73273A3788DEA342717C2640AC43C5A1CF862C9E09B206FCB3F6BB8022100B09972E75972D9148F2BDD462E5CB69B57C1214B88FC55CA638676C07CFC10D8032103579CA2E6D107522F012CD00B52B9A65FB46F0C57B9B8B6E377C48F526A44741AFFFFFFFF0380841E00000000001976A914BFB282C70C4191F45B5A6665CAD1682F2C9CFDFB88AC80841E00000000001976A9149857CC07BED33A5CF12B9C5E0500B675D500C81188ACE0FD1C00000000001976A91443C52850606C872403C0601E69FA34B26F62DB4A88AC00000000"));

        final TransactionInputInflater transactionInputInflater = new TransactionInputInflater();
        final TransactionInput transactionInput = transactionInputInflater.fromBytes(HexUtil.hexStringToByteArray("70AC0A1AE588AAF284C308D67CA92C69A39E2DB81337E563BF40C59DA0A5CF63000000006A4730440220360D20BAFF382059040BA9BE98947FD678FB08AAB2BB0C172EFA996FD8ECE9B702201B4FB0DE67F015C90E7AC8A193AEAB486A1F587E0F54D0FB9552EF7F5CE6CAEC032103579CA2E6D107522F012CD00B52B9A65FB46F0C57B9B8B6E377C48F526A44741AFFFFFFFF"));

        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(0, HexUtil.hexStringToByteArray("80841E00000000001976A914DCF72C4FD02F5A987CF9B02F2FABFCAC3341A87D88AC"));

        final MutableContext context = new MutableContext();
        {
            context.setBlockHeight(238798L);
            context.setTransaction(transaction);

            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(transactionOutput);
            context.setTransactionInputIndex(0);
        }

        final LockingScript lockingScript = new ImmutableLockingScript(HexUtil.hexStringToByteArray("76A914DCF72C4FD02F5A987CF9B02F2FABFCAC3341A87D88AC"));
        final UnlockingScript unlockingScript = new ImmutableUnlockingScript(HexUtil.hexStringToByteArray("4730440220360D20BAFF382059040BA9BE98947FD678FB08AAB2BB0C172EFA996FD8ECE9B702201B4FB0DE67F015C90E7AC8A193AEAB486A1F587E0F54D0FB9552EF7F5CE6CAEC032103579CA2E6D107522F012CD00B52B9A65FB46F0C57B9B8B6E377C48F526A44741A"));

        final ScriptRunner scriptRunner = new ScriptRunner();

        // Action
        final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);

        // Assert
        Assert.assertTrue(inputIsUnlocked);
    }

    @Test
    public void should_verify_transaction_AFD9C17F8913577EC3509520BD6E5D63E9C0FD2A5F70C787993B097BA6CA9FAE_1() {
        // NOTE: This transaction has multiple inputs using SIGHASH_SINGLE and is the second half of the above scenario...

        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("010000000370AC0A1AE588AAF284C308D67CA92C69A39E2DB81337E563BF40C59DA0A5CF63000000006A4730440220360D20BAFF382059040BA9BE98947FD678FB08AAB2BB0C172EFA996FD8ECE9B702201B4FB0DE67F015C90E7AC8A193AEAB486A1F587E0F54D0FB9552EF7F5CE6CAEC032103579CA2E6D107522F012CD00B52B9A65FB46F0C57B9B8B6E377C48F526A44741AFFFFFFFF7D815B6447E35FBEA097E00E028FB7DFBAD4F3F0987B4734676C84F3FCD0E804010000006B483045022100C714310BE1E3A9FF1C5F7CACC65C2D8E781FC3A88CEB063C6153BF950650802102200B2D0979C76E12BB480DA635F192CC8DC6F905380DD4AC1FF35A4F68F462FFFD032103579CA2E6D107522F012CD00B52B9A65FB46F0C57B9B8B6E377C48F526A44741AFFFFFFFF3F1F097333E4D46D51F5E77B53264DB8F7F5D2E18217E1099957D0F5AF7713EE010000006C493046022100B663499EF73273A3788DEA342717C2640AC43C5A1CF862C9E09B206FCB3F6BB8022100B09972E75972D9148F2BDD462E5CB69B57C1214B88FC55CA638676C07CFC10D8032103579CA2E6D107522F012CD00B52B9A65FB46F0C57B9B8B6E377C48F526A44741AFFFFFFFF0380841E00000000001976A914BFB282C70C4191F45B5A6665CAD1682F2C9CFDFB88AC80841E00000000001976A9149857CC07BED33A5CF12B9C5E0500B675D500C81188ACE0FD1C00000000001976A91443C52850606C872403C0601E69FA34B26F62DB4A88AC00000000"));

        final TransactionInputInflater transactionInputInflater = new TransactionInputInflater();
        final TransactionInput transactionInput = transactionInputInflater.fromBytes(HexUtil.hexStringToByteArray("7D815B6447E35FBEA097E00E028FB7DFBAD4F3F0987B4734676C84F3FCD0E804010000006B483045022100C714310BE1E3A9FF1C5F7CACC65C2D8E781FC3A88CEB063C6153BF950650802102200B2D0979C76E12BB480DA635F192CC8DC6F905380DD4AC1FF35A4F68F462FFFD032103579CA2E6D107522F012CD00B52B9A65FB46F0C57B9B8B6E377C48F526A44741AFFFFFFFF"));

        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(1, HexUtil.hexStringToByteArray("80841E00000000001976A914DCF72C4FD02F5A987CF9B02F2FABFCAC3341A87D88AC"));

        final MutableContext context = new MutableContext();
        {
            context.setBlockHeight(238798L);
            context.setTransaction(transaction);

            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(transactionOutput);
            context.setTransactionInputIndex(1);
        }

        final LockingScript lockingScript = new ImmutableLockingScript(HexUtil.hexStringToByteArray("76A914DCF72C4FD02F5A987CF9B02F2FABFCAC3341A87D88AC"));
        final UnlockingScript unlockingScript = new ImmutableUnlockingScript(HexUtil.hexStringToByteArray("483045022100C714310BE1E3A9FF1C5F7CACC65C2D8E781FC3A88CEB063C6153BF950650802102200B2D0979C76E12BB480DA635F192CC8DC6F905380DD4AC1FF35A4F68F462FFFD032103579CA2E6D107522F012CD00B52B9A65FB46F0C57B9B8B6E377C48F526A44741A"));

        final ScriptRunner scriptRunner = new ScriptRunner();

        // Action
        final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);

        // Assert
        Assert.assertTrue(inputIsUnlocked);
    }

    @Test
    public void should_verify_transaction_315AC7D4C26D69668129CC352851D9389B4A6868F1509C6C8B66BEAD11E2619F_1() {
        // NOTE: This transactions triggers the (infamous) bug that occurs when SIGHASH_SINGLE is used on an input that doesn't have a matching output.
        //  Original Discussion: https://bitcointalk.org/index.php?topic=260595.0

        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("0100000002DC38E9359BD7DA3B58386204E186D9408685F427F5E513666DB735AA8A6B2169000000006A47304402205D8FEEB312478E468D0B514E63E113958D7214FA572ACD87079A7F0CC026FC5C02200FA76EA05BF243AF6D0F9177F241CAF606D01FCFD5E62D6BEFBCA24E569E5C27032102100A1A9CA2C18932D6577C58F225580184D0E08226D41959874AC963E3C1B2FEFFFFFFFFDC38E9359BD7DA3B58386204E186D9408685F427F5E513666DB735AA8A6B2169010000006B4830450220087EDE38729E6D35E4F515505018E659222031273B7366920F393EE3AB17BC1E022100CA43164B757D1A6D1235F13200D4B5F76DD8FDA4EC9FC28546B2DF5B1211E8DF03210275983913E60093B767E85597CA9397FB2F418E57F998D6AFBBC536116085B1CBFFFFFFFF0140899500000000001976A914FCC9B36D38CF55D7D5B4EE4DDDB6B2C17612F48C88AC00000000"));

        final TransactionInputInflater transactionInputInflater = new TransactionInputInflater();
        final TransactionInput transactionInput = transactionInputInflater.fromBytes(HexUtil.hexStringToByteArray("DC38E9359BD7DA3B58386204E186D9408685F427F5E513666DB735AA8A6B2169010000006B4830450220087EDE38729E6D35E4F515505018E659222031273B7366920F393EE3AB17BC1E022100CA43164B757D1A6D1235F13200D4B5F76DD8FDA4EC9FC28546B2DF5B1211E8DF03210275983913E60093B767E85597CA9397FB2F418E57F998D6AFBBC536116085B1CBFFFFFFFF"));

        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(1, HexUtil.hexStringToByteArray("F0874B00000000001976A91433CEF61749D11BA2ADF091A5E045678177FE3A6D88AC"));

        final MutableContext context = new MutableContext();
        {
            context.setBlockHeight(247940L);
            context.setTransaction(transaction);

            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(transactionOutput);
            context.setTransactionInputIndex(1);
        }

        final LockingScript lockingScript = new ImmutableLockingScript(HexUtil.hexStringToByteArray("76A91433CEF61749D11BA2ADF091A5E045678177FE3A6D88AC"));
        final UnlockingScript unlockingScript = new ImmutableUnlockingScript(HexUtil.hexStringToByteArray("4830450220087EDE38729E6D35E4F515505018E659222031273B7366920F393EE3AB17BC1E022100CA43164B757D1A6D1235F13200D4B5F76DD8FDA4EC9FC28546B2DF5B1211E8DF03210275983913E60093B767E85597CA9397FB2F418E57F998D6AFBBC536116085B1CB"));

        final ScriptRunner scriptRunner = new ScriptRunner();

        // Action
        final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);

        // Assert
        Assert.assertTrue(inputIsUnlocked);
    }

    @Test
    public void should_verify_transaction_DA47BD83967D81F3CF6520F4FF81B3B6C4797BFE7AC2B5969AEDBF01A840CDA6_2() {
        // NOTE: This transaction spends the first control-group opcodes.
        //  This transaction also contains multiple OP_ELSE Opcodes in succession, asserting that "OP_IF .. OP_ELSE .. OP_ELSE .. OP_ENDIF" is a valid paradigm.

        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("0100000003FE1A25C8774C1E827F9EBDAE731FE609FF159D6F7C15094E1D467A99A01E03100000000002012AFFFFFFFF53A080075D834402E916390940782236B29D23DB6F52DFC940A12B3EFF99159C0000000000FFFFFFFF61E4ED95239756BBB98D11DCF973146BE0C17CC1CC94340DEB8BC4D44CD88E92000000000A516352676A675168948CFFFFFFFF0220AA4400000000001976A9149BC0BBDD3024DA4D0C38ED1AECF5C68DD1D3FA1288AC20AA4400000000001976A914169FF4804FD6596DEB974F360C21584AA1E19C9788AC00000000"));

        final TransactionInputInflater transactionInputInflater = new TransactionInputInflater();
        final TransactionInput transactionInput = transactionInputInflater.fromBytes(HexUtil.hexStringToByteArray("61E4ED95239756BBB98D11DCF973146BE0C17CC1CC94340DEB8BC4D44CD88E92000000000A516352676A675168948CFFFFFFFF"));

        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(0, HexUtil.hexStringToByteArray("40548900000000000763516751676A68"));

        final MutableContext context = new MutableContext();
        {
            context.setBlockHeight(249977L);
            context.setTransaction(transaction);

            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(transactionOutput);
            context.setTransactionInputIndex(2);
        }

        final LockingScript lockingScript = new ImmutableLockingScript(HexUtil.hexStringToByteArray("63516751676A68"));
        final UnlockingScript unlockingScript = new ImmutableUnlockingScript(HexUtil.hexStringToByteArray("516352676A675168948C"));

        final ScriptRunner scriptRunner = new ScriptRunner();

        // Action
        final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);

        // Assert
        Assert.assertTrue(inputIsUnlocked);
    }

    @Test
    public void should_verify_transaction_61A078472543E9DE9247446076320499C108B52307D8D0FAFBE53B5C4E32ACC4_0() {
        // 16cfb9bc7654ef1d7723e5c2722fc0c3d505045e OP_SIZE OP_DUP 1 OP_GREATERTHAN OP_VERIFY OP_NEGATE OP_HASH256 OP_HASH160 OP_SHA256 OP_SHA1 OP_RIPEMD160 OP_EQUAL

        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("0100000001D9A6B4DB2F4928ED172C22C8A2AB941F026277BF5DDE97E4C5A26E946BC9425300000000151416CFB9BC7654EF1D7723E5C2722FC0C3D505045EFFFFFFFF01D0E89600000000001976A914B0C1C1DE86419F7C6F3186935E6BD6CCB52B8EE588AC00000000"));

        final TransactionInputInflater transactionInputInflater = new TransactionInputInflater();
        final TransactionInput transactionInput = transactionInputInflater.fromBytes(HexUtil.hexStringToByteArray("D9A6B4DB2F4928ED172C22C8A2AB941F026277BF5DDE97E4C5A26E946BC9425300000000151416CFB9BC7654EF1D7723E5C2722FC0C3D505045EFFFFFFFF"));

        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(0, HexUtil.hexStringToByteArray("E00F9700000000000C827651A0698FAAA9A8A7A687"));

        final MutableContext context = new MutableContext();
        {
            context.setBlockHeight(251685L);
            context.setTransaction(transaction);

            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(transactionOutput);
            context.setTransactionInputIndex(0);
        }

        final LockingScript lockingScript = new ImmutableLockingScript(HexUtil.hexStringToByteArray("827651A0698FAAA9A8A7A687"));
        final UnlockingScript unlockingScript = new ImmutableUnlockingScript(HexUtil.hexStringToByteArray("1416CFB9BC7654EF1D7723E5C2722FC0C3D505045E"));

        final ScriptRunner scriptRunner = new ScriptRunner();

        // Action
        final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);

        // Assert
        Assert.assertTrue(inputIsUnlocked);
    }

    @Test
    public void should_verify_transaction_54FABD73F1D20C980A0686BF0035078E07F69C58437E4D586FB29AA0BEE9814F_0() {
        // NOTE: This transactions spends the first IS_WITHIN_RANGE, and ALT_STACK(s) opcode.

        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("01000000010C0E314BD7BB14721B3CFD8E487CD6866173354F87CA2CF4D13C8D3FEB4301A6000000004A483045022100D92E4B61452D91A473A43CDE4B469A472467C0BA0CBD5EBBA0834E4F4762810402204802B76B7783DB57AC1F61D2992799810E173E91055938750815B6D8A675902E014FFFFFFFFF0140548900000000001976A914A86E8EE2A05A44613904E18132E49B2448ADC4E688AC00000000"));

        final TransactionInputInflater transactionInputInflater = new TransactionInputInflater();
        final TransactionInput transactionInput = transactionInputInflater.fromBytes(HexUtil.hexStringToByteArray("0C0E314BD7BB14721B3CFD8E487CD6866173354F87CA2CF4D13C8D3FEB4301A6000000004A483045022100D92E4B61452D91A473A43CDE4B469A472467C0BA0CBD5EBBA0834E4F4762810402204802B76B7783DB57AC1F61D2992799810E173E91055938750815B6D8A675902E014FFFFFFFFF"));

        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(0, HexUtil.hexStringToByteArray("40548900000000002D76009F69905160A56B210378D430274F8C5EC1321338151E9F27F4C676A008BDF8638D07C0B6BE9AB35C71AD6C"));

        final MutableContext context = new MutableContext();
        {
            context.setBlockHeight(256962L);
            context.setTransaction(transaction);

            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(transactionOutput);
            context.setTransactionInputIndex(0);
        }

        final LockingScript lockingScript = new ImmutableLockingScript(HexUtil.hexStringToByteArray("76009F69905160A56B210378D430274F8C5EC1321338151E9F27F4C676A008BDF8638D07C0B6BE9AB35C71AD6C"));
        final UnlockingScript unlockingScript = new ImmutableUnlockingScript(HexUtil.hexStringToByteArray("483045022100D92E4B61452D91A473A43CDE4B469A472467C0BA0CBD5EBBA0834E4F4762810402204802B76B7783DB57AC1F61D2992799810E173E91055938750815B6D8A675902E014F"));

        final ScriptRunner scriptRunner = new ScriptRunner();

        // Action
        final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);

        // Assert
        Assert.assertTrue(inputIsUnlocked);
    }

    @Test
    public void should_verify_transaction_567A53D1CE19CE3D07711885168484439965501536D0D0294C5D46D46C10E53B_0() {
        // P2SH: (0x6E OP_DYNAMIC_VALUE-COPY_2ND_THEN_1ST) (0x87 OP_COMPARISON-IS_EQUAL) (0x91 OP_ARITHMETIC-NOT) (0x69 OP_CONTROL-VERIFY) (0x90 OP_ARITHMETIC-ABSOLUTE_VALUE) (0x7C OP_STACK-SWAP_1ST_WITH_2ND) (0x90 OP_ARITHMETIC-ABSOLUTE_VALUE) (0x87 OP_COMPARISON-IS_EQUAL)

        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("0100000001F18CDA90BBBCFB031C65CEDA17C82DC046C7DB0B96242BA4C5B53C411D8C056E020000000C510181086E879169907C9087FFFFFFFF01A0BB0D00000000001976A9149BC0BBDD3024DA4D0C38ED1AECF5C68DD1D3FA1288AC00000000"));

        final TransactionInputInflater transactionInputInflater = new TransactionInputInflater();
        final TransactionInput transactionInput = transactionInputInflater.fromBytes(HexUtil.hexStringToByteArray("F18CDA90BBBCFB031C65CEDA17C82DC046C7DB0B96242BA4C5B53C411D8C056E020000000C510181086E879169907C9087FFFFFFFF"));

        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(2, HexUtil.hexStringToByteArray("40420F000000000017A914FE441065B6532231DE2FAC563152205EC4F59C7487"));

        final MutableContext context = new MutableContext();
        {
            context.setBlockHeight(257728L);
            context.setTransaction(transaction);

            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(transactionOutput);
            context.setTransactionInputIndex(0);
        }

        final LockingScript lockingScript = new ImmutableLockingScript(HexUtil.hexStringToByteArray("A914FE441065B6532231DE2FAC563152205EC4F59C7487"));
        final UnlockingScript unlockingScript = new ImmutableUnlockingScript(HexUtil.hexStringToByteArray("510181086E879169907C9087"));

        final ScriptRunner scriptRunner = new ScriptRunner();

        // Action
        final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);

        // Assert
        Assert.assertTrue(inputIsUnlocked);
    }

    @Test
    public void should_verify_transaction_E218970E8F810BE99D60AA66262A1D382BC4B1A26A69AF07AC47D622885DB1A7_0() {
        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("01000000017047D51EB2671F08BE60033DC273DA6BF165AEAE6F0D2B2C901CCEDC592FC84E000000008B48304502210085807B5A614A1A2FAF0209C7D95BF046393C19BBBDB2CCAFD8CF1E87B906429E02204405C1B759E7A44BDFDD053198F5307F07830E54110BAC58C36B40A19AD8CD3A044104BB8F7EBE793C32E49C8F2B929B09CA09EE2B4F121B32C9DFCA121450BC2B6762C75ECE327C6724C30BFD14430AB4803371185E9060721DEFF8BDFA7F2CE5D751FFFFFFFF0100710200000000001976A9141E2F6AF9A8564C0CB58B8662DC2C63E70BD8B35288AC00000000"));

        final TransactionInputInflater transactionInputInflater = new TransactionInputInflater();
        final TransactionInput transactionInput = transactionInputInflater.fromBytes(HexUtil.hexStringToByteArray("7047D51EB2671F08BE60033DC273DA6BF165AEAE6F0D2B2C901CCEDC592FC84E000000008B48304502210085807B5A614A1A2FAF0209C7D95BF046393C19BBBDB2CCAFD8CF1E87B906429E02204405C1B759E7A44BDFDD053198F5307F07830E54110BAC58C36B40A19AD8CD3A044104BB8F7EBE793C32E49C8F2B929B09CA09EE2B4F121B32C9DFCA121450BC2B6762C75ECE327C6724C30BFD14430AB4803371185E9060721DEFF8BDFA7F2CE5D751FFFFFFFF"));

        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(2, HexUtil.hexStringToByteArray("50340300000000001976A914A7A120D4358DD1E8BC9566329EAD42C4F394CCFC88AC"));

        final MutableContext context = new MutableContext();
        {
            context.setBlockHeight(260789L);
            context.setTransaction(transaction);

            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(transactionOutput);
            context.setTransactionInputIndex(0);
        }

        final LockingScript lockingScript = new ImmutableLockingScript(HexUtil.hexStringToByteArray("76A914A7A120D4358DD1E8BC9566329EAD42C4F394CCFC88AC"));
        final UnlockingScript unlockingScript = new ImmutableUnlockingScript(HexUtil.hexStringToByteArray("48304502210085807B5A614A1A2FAF0209C7D95BF046393C19BBBDB2CCAFD8CF1E87B906429E02204405C1B759E7A44BDFDD053198F5307F07830E54110BAC58C36B40A19AD8CD3A044104BB8F7EBE793C32E49C8F2B929B09CA09EE2B4F121B32C9DFCA121450BC2B6762C75ECE327C6724C30BFD14430AB4803371185E9060721DEFF8BDFA7F2CE5D751"));

        final ScriptRunner scriptRunner = new ScriptRunner();

        // Action
        final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);

        // Assert
        Assert.assertTrue(inputIsUnlocked);
    }

    @Test
    public void should_verify_transaction_BA4F9786BB34571BD147448AB3C303AE4228B9C22C89E58CC50E26FF7538BF80_0() {
        // Non-Standard HashType: 0x50

        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("010000000140ED240A0A018469A1CDD3656800C23EC796D698D82EA336C4B6FB72B141C9CD000000008B483045022100DE3B4C67E8A3EB09F18E8B5834257C0A27812DF490365B8AC0E30E1D3DCC01630220426997EE904736647AE2E0B93CB2A11511F7B33E8F8A8CE0C5265CBD5B113AE8504104B1796B0E02F327E1A6F61ABDFF028374DE9C80D6189460B0B7035752A2D00364FB19F16868BA34A4E93350E49E6FF8BFB48D23AB15F14871B01D6562F69B9973FFFFFFFF0300E1F505000000001976A914AC625F248F3BE5C1C17767B8B2B93DD03553984788ACE0E2CF17000000001976A9142C00769E224AC558CF0E726C8E4D6AA9D34F6E5688AC107A0700000000001976A9144BD0AC767D24ACC4C5AF736767B7B3ACD1A6776188AC00000000"));

        final TransactionInputInflater transactionInputInflater = new TransactionInputInflater();
        final TransactionInput transactionInput = transactionInputInflater.fromBytes(HexUtil.hexStringToByteArray("40ED240A0A018469A1CDD3656800C23EC796D698D82EA336C4B6FB72B141C9CD000000008B483045022100DE3B4C67E8A3EB09F18E8B5834257C0A27812DF490365B8AC0E30E1D3DCC01630220426997EE904736647AE2E0B93CB2A11511F7B33E8F8A8CE0C5265CBD5B113AE8504104B1796B0E02F327E1A6F61ABDFF028374DE9C80D6189460B0B7035752A2D00364FB19F16868BA34A4E93350E49E6FF8BFB48D23AB15F14871B01D6562F69B9973FFFFFFFF"));

        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(2, HexUtil.hexStringToByteArray("0065CD1D000000001976A914990A8E1EB7A69C41602BD46FED56B6A38FD9BC1E88AC"));

        final MutableContext context = new MutableContext();
        {
            context.setBlockHeight(264085L);
            context.setTransaction(transaction);

            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(transactionOutput);
            context.setTransactionInputIndex(0);
        }

        final LockingScript lockingScript = new ImmutableLockingScript(HexUtil.hexStringToByteArray("76A914990A8E1EB7A69C41602BD46FED56B6A38FD9BC1E88AC"));
        final UnlockingScript unlockingScript = new ImmutableUnlockingScript(HexUtil.hexStringToByteArray("483045022100DE3B4C67E8A3EB09F18E8B5834257C0A27812DF490365B8AC0E30E1D3DCC01630220426997EE904736647AE2E0B93CB2A11511F7B33E8F8A8CE0C5265CBD5B113AE8504104B1796B0E02F327E1A6F61ABDFF028374DE9C80D6189460B0B7035752A2D00364FB19F16868BA34A4E93350E49E6FF8BFB48D23AB15F14871B01D6562F69B9973"));

        final ScriptRunner scriptRunner = new ScriptRunner();

        // Action
        final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);

        // Assert
        Assert.assertTrue(inputIsUnlocked);
    }

    @Test
    public void should_verify_transaction_9FB65B7304AAA77AC9580823C2C06B259CC42591E5CCE66D76A81B6F51CC5C28_0() {
        // NOTE: This transaction expects an invalid scriptSignature.  The invalid scriptSignature is permitted, and expects a zero to be placed on the stack.

        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("0100000001545959FEA5F64C7D0CC4199A175EBFA6EB29415CF72ACD7676DEDAAD8170CEA6000000008C20CA42095840735E89283FEC298E62AC2DDEA9B5F34A8CBB7097AD965B87568100201B1B01DC829177DA4A14551D2FC96A9DB00C6501EDFA12F22CD9CEFD335C227F483045022100940A7A74D00D590DC6743C8D7416475845611047BED013DB2C4F80C96261576A02202B223572A37C2EEE65FEFABF701B2F39E3DF2CBA5D136EEF0485D9A24F49C0AA0100FFFFFFFF010071020000000000232103611F9A45C18F28F06F19076AD571C344C82CE8FCFE34464CF8085217A2D294A6AC00000000"));

        final TransactionInputInflater transactionInputInflater = new TransactionInputInflater();
        final TransactionInput transactionInput = transactionInputInflater.fromBytes(HexUtil.hexStringToByteArray("545959FEA5F64C7D0CC4199A175EBFA6EB29415CF72ACD7676DEDAAD8170CEA6000000008C20CA42095840735E89283FEC298E62AC2DDEA9B5F34A8CBB7097AD965B87568100201B1B01DC829177DA4A14551D2FC96A9DB00C6501EDFA12F22CD9CEFD335C227F483045022100940A7A74D00D590DC6743C8D7416475845611047BED013DB2C4F80C96261576A02202B223572A37C2EEE65FEFABF701B2F39E3DF2CBA5D136EEF0485D9A24F49C0AA0100FFFFFFFF"));

        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(2, HexUtil.hexStringToByteArray("1098020000000000B52102085C6600657566ACC2D6382A47BC3F324008D2AA10940DD7705A48AA2A5A5E33AC7C2103F5D0FB955F95DD6BE6115CE85661DB412EC6A08ABCBFCE7DA0BA8297C6CC0EC4AC7C5379A820D68DF9E32A147CFFA36193C6F7C43A1C8C69CDA530E1C6DB354BFABDCFEFAF3C875379A820F531F3041D3136701EA09067C53E7159C8F9B2746A56C3D82966C54BBC553226879A5479827701200122A59A5379827701200122A59A6353798277537982778779679A68"));

        final MutableContext context = new MutableContext();
        {
            context.setBlockHeight(268562L);
            context.setTransaction(transaction);

            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(transactionOutput);
            context.setTransactionInputIndex(0);
        }

        final LockingScript lockingScript = new ImmutableLockingScript(HexUtil.hexStringToByteArray("2102085C6600657566ACC2D6382A47BC3F324008D2AA10940DD7705A48AA2A5A5E33AC7C2103F5D0FB955F95DD6BE6115CE85661DB412EC6A08ABCBFCE7DA0BA8297C6CC0EC4AC7C5379A820D68DF9E32A147CFFA36193C6F7C43A1C8C69CDA530E1C6DB354BFABDCFEFAF3C875379A820F531F3041D3136701EA09067C53E7159C8F9B2746A56C3D82966C54BBC553226879A5479827701200122A59A5379827701200122A59A6353798277537982778779679A68"));
        final UnlockingScript unlockingScript = new ImmutableUnlockingScript(HexUtil.hexStringToByteArray("20CA42095840735E89283FEC298E62AC2DDEA9B5F34A8CBB7097AD965B87568100201B1B01DC829177DA4A14551D2FC96A9DB00C6501EDFA12F22CD9CEFD335C227F483045022100940A7A74D00D590DC6743C8D7416475845611047BED013DB2C4F80C96261576A02202B223572A37C2EEE65FEFABF701B2F39E3DF2CBA5D136EEF0485D9A24F49C0AA0100"));

        final ScriptRunner scriptRunner = new ScriptRunner();

        // Action
        final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);

        // Assert
        Assert.assertTrue(inputIsUnlocked);
    }

    @Test
    public void should_verify_transaction_AEF4CF7ABCD4344AE612D5F27735010A26E5102AF20A97A5F43802583D72EB78_0() {
        // NOTE: This transactions spends the first transaction containing OP_ROT (ROTATE_TOP_3)...

        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("0100000001A48A2CF33ECD5134784418646820BD69D6C1CEFF36DD5497200073D56B810D5400000000AB47304402207D9184B4E4A1FE11BCB7743DB85F1F82FF00AB8C54F58B44DE2AE50D8B1C35F3022019D9F60CD927A317E258D7EE3B9CEBA49CD2A6BF15C195E4FC5D5F5915B83B620120CA42095840735E89283FEC298E62AC2DDEA9B5F34A8CBB7097AD965B87568123201B1B01DC829177DA4A14551D2FC96A9DB00C6501EDFA12F22CD9CEFD335C227F202C2C01DC829177DA4A14551D2FC96A9DB00C6501EDFA12F22CD9CEFD335C338EFFFFFFFF012030050000000000434104C9CE67FF2DF2CD6BE5F58345B4E311C5F10AAB49D3CF3F73E8DCAC1F9CD0DE966E924BE091E7BC854AEF0D0BAAFA80FE5F2D6AF56B1788E1E8EC8D241B41C40DAC00000000"));

        final TransactionInputInflater transactionInputInflater = new TransactionInputInflater();
        final TransactionInput transactionInput = transactionInputInflater.fromBytes(HexUtil.hexStringToByteArray("A48A2CF33ECD5134784418646820BD69D6C1CEFF36DD5497200073D56B810D5400000000AB47304402207D9184B4E4A1FE11BCB7743DB85F1F82FF00AB8C54F58B44DE2AE50D8B1C35F3022019D9F60CD927A317E258D7EE3B9CEBA49CD2A6BF15C195E4FC5D5F5915B83B620120CA42095840735E89283FEC298E62AC2DDEA9B5F34A8CBB7097AD965B87568123201B1B01DC829177DA4A14551D2FC96A9DB00C6501EDFA12F22CD9CEFD335C227F202C2C01DC829177DA4A14551D2FC96A9DB00C6501EDFA12F22CD9CEFD335C338EFFFFFFFF"));

        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(2, HexUtil.hexStringToByteArray("3057050000000000FD6301827D01200123A569A820197BF68FB520E8D3419DC1D4AC1EB89E7DFD7CFE561C19ABF7611D7626D9F02C887C827D01200123A569A820F531F3041D3136701EA09067C53E7159C8F9B2746A56C3D82966C54BBC553226887B827D01200123A569A820527CCDD755DCCCF03192383624E0A7D0263815CE2ECF1F69CB0423AB7E6F0F3E8893930160947652A0635394687652A0635394684104D4BF4642F56FC7AF0D2382E2CAC34FA16ED3321633F91D06128F0E5C0D17479778CC1F2CC7E4A0C6F1E72D905532E8E127A031BB9794B3EF9B68B657F51CC6914104208A50909284AEDE02AD107BB1F52175B025CDF0453537B686433BCADE6D3E210B6C82BCBDF8676B2161687E232F5D9AFDAA4ED7B3E3BF9608D41B40EBDE6ED44104C9CE67FF2DF2CD6BE5F58345B4E311C5F10AAB49D3CF3F73E8DCAC1F9CD0DE966E924BE091E7BC854AEF0D0BAAFA80FE5F2D6AF56B1788E1E8EC8D241B41C40D537A7A537A7CAD"));

        final MutableContext context = new MutableContext();
        {
            context.setBlockHeight(269615L);
            context.setTransaction(transaction);

            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(transactionOutput);
            context.setTransactionInputIndex(0);
        }

        final LockingScript lockingScript = new ImmutableLockingScript(HexUtil.hexStringToByteArray("827D01200123A569A820197BF68FB520E8D3419DC1D4AC1EB89E7DFD7CFE561C19ABF7611D7626D9F02C887C827D01200123A569A820F531F3041D3136701EA09067C53E7159C8F9B2746A56C3D82966C54BBC553226887B827D01200123A569A820527CCDD755DCCCF03192383624E0A7D0263815CE2ECF1F69CB0423AB7E6F0F3E8893930160947652A0635394687652A0635394684104D4BF4642F56FC7AF0D2382E2CAC34FA16ED3321633F91D06128F0E5C0D17479778CC1F2CC7E4A0C6F1E72D905532E8E127A031BB9794B3EF9B68B657F51CC6914104208A50909284AEDE02AD107BB1F52175B025CDF0453537B686433BCADE6D3E210B6C82BCBDF8676B2161687E232F5D9AFDAA4ED7B3E3BF9608D41B40EBDE6ED44104C9CE67FF2DF2CD6BE5F58345B4E311C5F10AAB49D3CF3F73E8DCAC1F9CD0DE966E924BE091E7BC854AEF0D0BAAFA80FE5F2D6AF56B1788E1E8EC8D241B41C40D537A7A537A7CAD"));
        final UnlockingScript unlockingScript = new ImmutableUnlockingScript(HexUtil.hexStringToByteArray("47304402207D9184B4E4A1FE11BCB7743DB85F1F82FF00AB8C54F58B44DE2AE50D8B1C35F3022019D9F60CD927A317E258D7EE3B9CEBA49CD2A6BF15C195E4FC5D5F5915B83B620120CA42095840735E89283FEC298E62AC2DDEA9B5F34A8CBB7097AD965B87568123201B1B01DC829177DA4A14551D2FC96A9DB00C6501EDFA12F22CD9CEFD335C227F202C2C01DC829177DA4A14551D2FC96A9DB00C6501EDFA12F22CD9CEFD335C338E"));

        final ScriptRunner scriptRunner = new ScriptRunner();

        // Action
        final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);

        // Assert
        Assert.assertTrue(inputIsUnlocked);
    }
}
