package com.softwareverde.bitcoin.transaction.dsproof;

import com.softwareverde.bitcoin.bip.CoreUpgradeSchedule;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.message.type.dsproof.DoubleSpendProofMessage;
import com.softwareverde.bitcoin.server.message.type.dsproof.DoubleSpendProofMessageInflater;
import com.softwareverde.bitcoin.server.message.type.dsproof.DoubleSpendProofPreimage;
import com.softwareverde.bitcoin.server.message.type.dsproof.DoubleSpendProofPreimageInflater;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputInflater;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.HexUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DoubleSpendProofPreimageValidatorTests extends UnitTest {
    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_validate_dsproof_preimages_generated_by_bchn() {
        // Setup
        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final TransactionInflater transactionInflater = new TransactionInflater();
        final DoubleSpendProofPreimageInflater doubleSpendProofPreimageInflater = new DoubleSpendProofPreimageInflater();

        final Long blockHeight = 680525L;
        final MedianBlockTime medianBlockTime = MedianBlockTime.fromSeconds(1616818639L);
        final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(Sha256Hash.fromHexString("00334930D2356064E43D376D998BE674301AC5ECEFD60D3CC2E261FF938137BB"), 0);
        final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(0, ByteArray.fromHexString("EF201400000000001976A914055934E30C3766DAAFF14581DE4C17AF3E15B67788AC"));
        final Transaction transaction = transactionInflater.fromBytes(ByteArray.fromHexString("0200000001BB378193FF61E2C23C0DD6EFECC51A3074E68B996D373DE4646035D230493300000000006B483045022100C7D72177641F40A8E5C6C168DCCE7A8EBC174667238856B30EE6657C04B68AE602205C281A9CA15E5292720A27C27AFF89F809B961C0D86A63C30CDCB06286658CDC4121030095B240D954005A336C9C9D0F45B68174A156E4BF5775D32D1B60D021AE01F9FFFFFFFF01ED1F1400000000001976A914055934E30C3766DAAFF14581DE4C17AF3E15B67788AC00000000"));
        final DoubleSpendProofPreimage doubleSpendProofPreimage0 = doubleSpendProofPreimageInflater.fromBytes(ByteArray.fromHexString("02000000FFFFFFFF0000000007EFEE38FB0076EA7258F70D3E11B19F0636B1C998CA5D01F0FB9A3C7298A07D3BB13029CE7B1F559EF5E747FCAC439F1455A2EC7C5F09B72290795E7066504443D6A08791FB21CFCD84B904669E2877D1012211FFCDBA3EB14B562173C0646301473044022038045D988BB6BD9CF8616E8CD3B380D7AC358E71E2ADFB1F06BC7288544C9EAF02205C1F78F2FF04314A4E516D02D58897BCE120C2939950518D72BB2406F11FDC7C41"));
        final DoubleSpendProofPreimage doubleSpendProofPreimage1 = doubleSpendProofPreimageInflater.fromBytes(ByteArray.fromHexString("02000000FFFFFFFF0000000007EFEE38FB0076EA7258F70D3E11B19F0636B1C998CA5D01F0FB9A3C7298A07D3BB13029CE7B1F559EF5E747FCAC439F1455A2EC7C5F09B72290795E70665044F8CEDFF543DEA491E1A0CA101CE699BBD7AB45D6CBECBD9348C892B08752678801483045022100C7D72177641F40A8E5C6C168DCCE7A8EBC174667238856B30EE6657C04B68AE602205C281A9CA15E5292720A27C27AFF89F809B961C0D86A63C30CDCB06286658CDC41"));

        final DoubleSpendProofPreimageValidator doubleSpendProofPreimageValidator = new DoubleSpendProofPreimageValidator(blockHeight, medianBlockTime, new CoreUpgradeSchedule());

        // Action
        final Boolean doubleSpendProofPreimage0IsValid = doubleSpendProofPreimageValidator.validateDoubleSpendProof(transactionOutputIdentifier, transactionOutput, transaction, doubleSpendProofPreimage0);
        final Boolean doubleSpendProofPreimage1IsValid = doubleSpendProofPreimageValidator.validateDoubleSpendProof(transactionOutputIdentifier, transactionOutput, transaction, doubleSpendProofPreimage1);

        // Assert
        Assert.assertTrue(doubleSpendProofPreimage0IsValid);
        Assert.assertTrue(doubleSpendProofPreimage1IsValid);
    }

    @Test
    public void should_validate_dsproof_generated_by_bchn() {
        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final DoubleSpendProofMessageInflater doubleSpendProofMessageInflater = new DoubleSpendProofMessageInflater();

        final DoubleSpendProofMessage doubleSpendProofMessage = doubleSpendProofMessageInflater.fromBytes(HexUtil.hexStringToByteArray("E3E1F3E8647370726F6F662D626574618E010000511293FA3F24C7EC86D52F405F2C9CCDD9DDD98562013268BFCCC7A7412C053D2ED710600000000002000000FFFFFFFF0000000096DAF83D3751CB690D2FA67A5ED2998CFC5F369A1FD465585692F4CFA38FB0793BB13029CE7B1F559EF5E747FCAC439F1455A2EC7C5F09B72290795E70665044D6C189A3B9F117043AF9F4BE822A4E19FB4D4D275D14FB7269D916513E067A8401473044022027EF65A57E034C8BFACF0A86C5FCF732EA76160A217838F558BD80BC99B8E7CB022024D271AC49002AFE60CFC489CE8C57032D2D2E97A85215CBE3B5E57855DC5E634102000000FFFFFFFF0000000096DAF83D3751CB690D2FA67A5ED2998CFC5F369A1FD465585692F4CFA38FB0793BB13029CE7B1F559EF5E747FCAC439F1455A2EC7C5F09B72290795E70665044C12A508ABD96991F2F1166EDD4D507D4828656257752698875FC295E499796EA0147304402207BD5E83827AFF32468FD76B8D3F53C573D24B0721637306600147F0D2E7D8AF5022072D70BED9C73A32297A6B0E805D61E4A5D60D785B7901CCC360278AB9AAC002A41"));
        final DoubleSpendProof doubleSpendProof = doubleSpendProofMessage.getDoubleSpendProof();

        final Transaction transaction = transactionInflater.fromBytes(ByteArray.fromHexString("02000000013F24C7EC86D52F405F2C9CCDD9DDD98562013268BFCCC7A7412C053D2ED71060000000006A47304402207BD5E83827AFF32468FD76B8D3F53C573D24B0721637306600147F0D2E7D8AF5022072D70BED9C73A32297A6B0E805D61E4A5D60D785B7901CCC360278AB9AAC002A4121030095B240D954005A336C9C9D0F45B68174A156E4BF5775D32D1B60D021AE01F9FFFFFFFF01E91D1400000000001976A914055934E30C3766DAAFF14581DE4C17AF3E15B67788AC00000000"));
        final Transaction transactionBeingSpent = transactionInflater.fromBytes(ByteArray.fromHexString("0200000001C0A46A7C5C2F4041D162766B9F9FF1DE79E34851A0A8E1315D81E3DE17267F62000000006A473044022038D941167A18E74631530B3A135ED23AAB4EC5829F0A9E8BB462C9A43FC14720022023CABAFBA318F831994B053511198E18836F38F85F676B765204164F050061184121030095B240D954005A336C9C9D0F45B68174A156E4BF5775D32D1B60D021AE01F9FFFFFFFF01EB1E1400000000001976A914055934E30C3766DAAFF14581DE4C17AF3E15B67788AC00000000"));

        final Long blockHeight = 680533L;
        final MedianBlockTime medianBlockTime = MedianBlockTime.fromSeconds(1616820954L);
        final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(Sha256Hash.fromHexString("6010D72E3D052C41A7C7CCBF6832016285D9DDD9CD9C2C5F402FD586ECC7243F"), 0);
        final TransactionOutput transactionOutput = transactionBeingSpent.getTransactionOutputs().get(0);
        final DoubleSpendProofPreimage doubleSpendProofPreimage0 = doubleSpendProof.getDoubleSpendProofPreimage0();
        final DoubleSpendProofPreimage doubleSpendProofPreimage1 = doubleSpendProof.getDoubleSpendProofPreimage1();

        final DoubleSpendProofPreimageValidator doubleSpendProofPreimageValidator = new DoubleSpendProofPreimageValidator(blockHeight, medianBlockTime, new CoreUpgradeSchedule());

        // Action
        final Boolean preimagesAreInCanonicalOrder = DoubleSpendProof.arePreimagesInCanonicalOrder(doubleSpendProofPreimage0, doubleSpendProofPreimage1);
        final Boolean doubleSpendProofPreimage0IsValid = doubleSpendProofPreimageValidator.validateDoubleSpendProof(transactionOutputIdentifier, transactionOutput, transaction, doubleSpendProofPreimage0);
        final Boolean doubleSpendProofPreimage1IsValid = doubleSpendProofPreimageValidator.validateDoubleSpendProof(transactionOutputIdentifier, transactionOutput, transaction, doubleSpendProofPreimage1);

        // Assert
        Assert.assertTrue(preimagesAreInCanonicalOrder);
        Assert.assertTrue(doubleSpendProofPreimage0IsValid);
        Assert.assertTrue(doubleSpendProofPreimage1IsValid);
    }

    @Test
    public void should_not_validate_dsproof_with_preimages_in_reverse_order() throws Exception {
        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final DoubleSpendProofInflater doubleSpendProofInflater = new DoubleSpendProofInflater();

        final Transaction transactionBeingSpent = transactionInflater.fromBytes(ByteArray.fromHexString("020000000156EFDEA211A9B51EB4AFDD342CF6DBC971C329677899ABA67DC440744170943F000000006B483045022100EEFB7C3CFF281B85199CCBD195E792E5043BBC0F838F29F62CD4502E3C2ABB6402200A58FD3B711C22EE0483AFF38F399A772FDC8914C189A3B823C4B8AB3248B1EF4121030095B240D954005A336C9C9D0F45B68174A156E4BF5775D32D1B60D021AE01F9FFFFFFFF01E31A1400000000001976A914055934E30C3766DAAFF14581DE4C17AF3E15B67788AC00000000"));
        final TransactionOutput transactionOutputBeingSpent = transactionBeingSpent.getTransactionOutputs().get(0);

        // D90639268216CA2AC6548BCEDA561FC42CD17F25F7508C3E7B932460E09635AC
        final Transaction firstSeenTransaction = transactionInflater.fromBytes(ByteArray.fromHexString("020000000180D9D013F245CAA214D7AE2D729BB8D3CE2B955AB7D7D646AB10ADDA2FC60143000000006A473044022066B445798A1E1227C5259ACD7F7028F4EF52B33F3CE98935F00E54E4019CC547022028E5352627E29D7228B06D823A508C9A66326837CCC30BAD9C064C37089CEC5F4121030095B240D954005A336C9C9D0F45B68174A156E4BF5775D32D1B60D021AE01F9FFFFFFFF01E1191400000000001976A914055934E30C3766DAAFF14581DE4C17AF3E15B67788AC00000000"));

        final DoubleSpendProof doubleSpendProof = doubleSpendProofInflater.fromBytes(ByteArray.fromHexString("80D9D013F245CAA214D7AE2D729BB8D3CE2B955AB7D7D646AB10ADDA2FC601430000000002000000FFFFFFFF0000000010063C63373C3C9FC453F36739E4128FAC03AE6CE318AE8C72FC4B23B6A4A0D13BB13029CE7B1F559EF5E747FCAC439F1455A2EC7C5F09B72290795E7066504432F2B117A860DA57AA32FCC815A5904A2E948A2C92CA3AAC3F80532DD54299A50147304402204578DE7E28F946DE9916A6AC256B7C9D6968876E3D7C0E340D3863C713A2171802205BAE9FFC8C72EAC812590AA8CA3BB2E9CC322BF2259236B8F8BDC294CA2AD9774102000000FFFFFFFF0000000010063C63373C3C9FC453F36739E4128FAC03AE6CE318AE8C72FC4B23B6A4A0D13BB13029CE7B1F559EF5E747FCAC439F1455A2EC7C5F09B72290795E7066504473DFDF808DE7D14B64DD9539AA3F58590E62C0FE3C9C822BBD894D7D4E18811C01473044022066B445798A1E1227C5259ACD7F7028F4EF52B33F3CE98935F00E54E4019CC547022028E5352627E29D7228B06D823A508C9A66326837CCC30BAD9C064C37089CEC5F41"));

        final DoubleSpendProofValidator.Context context = new DoubleSpendProofValidator.Context(
            680880L,
            MedianBlockTime.fromSeconds(1617040573L),
            transactionOutputBeingSpent,
            firstSeenTransaction,
            new CoreUpgradeSchedule()
        );

        final DoubleSpendProofValidator doubleSpendProofValidator = new DoubleSpendProofValidator(context);

        // Action
        final Boolean doubleSpendProofIsValid = doubleSpendProofValidator.isDoubleSpendValid(doubleSpendProof);

        // Assert
        Assert.assertFalse(doubleSpendProofIsValid);
    }

    @Test
    public void should_swap_preimage_order_when_provided_out_of_order() throws Exception {
        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();

        final Transaction transactionBeingSpent = transactionInflater.fromBytes(ByteArray.fromHexString("020000000156EFDEA211A9B51EB4AFDD342CF6DBC971C329677899ABA67DC440744170943F000000006B483045022100EEFB7C3CFF281B85199CCBD195E792E5043BBC0F838F29F62CD4502E3C2ABB6402200A58FD3B711C22EE0483AFF38F399A772FDC8914C189A3B823C4B8AB3248B1EF4121030095B240D954005A336C9C9D0F45B68174A156E4BF5775D32D1B60D021AE01F9FFFFFFFF01E31A1400000000001976A914055934E30C3766DAAFF14581DE4C17AF3E15B67788AC00000000"));
        final TransactionOutput transactionOutputBeingSpent = transactionBeingSpent.getTransactionOutputs().get(0);

        // D90639268216CA2AC6548BCEDA561FC42CD17F25F7508C3E7B932460E09635AC
        final Transaction firstSeenTransaction = transactionInflater.fromBytes(ByteArray.fromHexString("020000000180D9D013F245CAA214D7AE2D729BB8D3CE2B955AB7D7D646AB10ADDA2FC60143000000006A473044022066B445798A1E1227C5259ACD7F7028F4EF52B33F3CE98935F00E54E4019CC547022028E5352627E29D7228B06D823A508C9A66326837CCC30BAD9C064C37089CEC5F4121030095B240D954005A336C9C9D0F45B68174A156E4BF5775D32D1B60D021AE01F9FFFFFFFF01E1191400000000001976A914055934E30C3766DAAFF14581DE4C17AF3E15B67788AC00000000"));

        // 49298445220AEEE97A5E747E0B7ECD0CDE26369742DE7B5755E5197F3A6E37D8
        final Transaction doubleSpendTransaction = transactionInflater.fromBytes(ByteArray.fromHexString("020000000180D9D013F245CAA214D7AE2D729BB8D3CE2B955AB7D7D646AB10ADDA2FC60143000000006A47304402204578DE7E28F946DE9916A6AC256B7C9D6968876E3D7C0E340D3863C713A2171802205BAE9FFC8C72EAC812590AA8CA3BB2E9CC322BF2259236B8F8BDC294CA2AD9774121030095B240D954005A336C9C9D0F45B68174A156E4BF5775D32D1B60D021AE01F9FFFFFFFF01E1191400000000001976A9144D766333BC326068E2ED6438AEDE3F68C0B64FCF88AC00000000"));

        final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(Sha256Hash.fromHexString("4301C62FDAAD10AB46D6D7B75A952BCED3B89B722DAED714A2CA45F213D0D980"), 0);
        final DoubleSpendProof doubleSpendProof = DoubleSpendProof.createDoubleSpendProof(transactionOutputIdentifier, ScriptType.PAY_TO_PUBLIC_KEY_HASH, firstSeenTransaction, doubleSpendTransaction);
        Assert.assertNotNull(doubleSpendProof);

        final DoubleSpendProofValidator.Context context = new DoubleSpendProofValidator.Context(
            680880L,
            MedianBlockTime.fromSeconds(1617040573L),
            transactionOutputBeingSpent,
            firstSeenTransaction,
            new CoreUpgradeSchedule()
        );

        final DoubleSpendProofValidator doubleSpendProofValidator = new DoubleSpendProofValidator(context);

        // Action
        final Boolean doubleSpendProofIsValid = doubleSpendProofValidator.isDoubleSpendValid(doubleSpendProof);

        // Assert
        Assert.assertTrue(doubleSpendProofIsValid);
    }
}
