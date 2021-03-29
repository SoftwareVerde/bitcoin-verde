package com.softwareverde.bitcoin.server.message.type.dsproof;

import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProof;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DoubleSpendProofMessageTests extends UnitTest {
    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception{
        super.after();
    }

    @Test
    public void should_inflate_double_spend_proof_message() throws Exception {
        // Setup
        final DoubleSpendProofMessageInflater doubleSpendProofMessageInflater = new DoubleSpendProofMessageInflater();

        //                                                 (HEADER)                                             (PAYLOAD)
        final ByteArray payload = ByteArray.fromHexString("E3E1F3E8647370726F6F662D6265746190010000AEFF2EC7" + "0B9D14B709AA59BD594EDCA17DB2951C6660EBC8DAA31CEAE233A5550314F1580000000001000000FFFFFFFF0000000037FC0D39E5FE250B5DE0F33D0FB454F532F1F96B26895C16961CD647A524CF1C3BB13029CE7B1F559EF5E747FCAC439F1455A2EC7C5F09B72290795E706650447DBAD1250B871EE78B8DCD80C8F33F4DDE3917A38D4FCF8094352BECDA7F0C0901483045022100B34A120E69BC933AE16C10DB0F565CB2DA1B80A9695A51707E8A80C9AA5C22BF02206C390CB328763AB9AB2D45F874D308AF2837D6D8CFC618AF76744B9EEB69C3934101000000FFFFFFFF0000000037FC0D39E5FE250B5DE0F33D0FB454F532F1F96B26895C16961CD647A524CF1C3BB13029CE7B1F559EF5E747FCAC439F1455A2EC7C5F09B72290795E706650440E2988B0EC1C79B25CFF60F2B52E08F77076E42A87E153F7D38E1A61857A2F7A01483045022100D9D22406611228D64E6B674DE8B16E802F8F789F8338130506C7741CDAE9116602202DC63A4F5F9E750EEC9DFC1557469BDA43D3491B358484E5C25992A381048A4941");

        // Action
        final DoubleSpendProofMessage doubleSpendProofMessage = doubleSpendProofMessageInflater.fromBytes(payload.getBytes());
        final ByteArray deflatedBytes = doubleSpendProofMessage.getBytes();

        // Assert
        Assert.assertNotNull(doubleSpendProofMessage);
        Assert.assertEquals(payload, deflatedBytes);
    }

    @Test
    public void should_inflate_double_spend_proof_message_2() throws Exception {
        // Setup
        final DoubleSpendProofMessageInflater doubleSpendProofMessageInflater = new DoubleSpendProofMessageInflater();

        final Sha256Hash expectedDoubleSpendProofHash = Sha256Hash.fromHexString("2B195745B876ABE7BEFFA19826403FE63E40C82C38610F96390749F61F4F8A4E");
        final String messageHeaderHexString = "E3E1F3E8647370726F6F662D626574618F0100004E8A4F1F";
        final String dsProofHexString = "C0A46A7C5C2F4041D162766B9F9FF1DE79E34851A0A8E1315D81E3DE17267F620000000002000000FFFFFFFF000000005BCBDC3AF32F7CF758D3CBA5C79E4C90D41334ABD05C683E6B16A284C606F3753BB13029CE7B1F559EF5E747FCAC439F1455A2EC7C5F09B72290795E70665044F46C132E62841F756C529035596092ACF8CBAB948537E0C8A979CA36D3F40F0001473044022038D941167A18E74631530B3A135ED23AAB4EC5829F0A9E8BB462C9A43FC14720022023CABAFBA318F831994B053511198E18836F38F85F676B765204164F050061184102000000FFFFFFFF000000005BCBDC3AF32F7CF758D3CBA5C79E4C90D41334ABD05C683E6B16A284C606F3753BB13029CE7B1F559EF5E747FCAC439F1455A2EC7C5F09B72290795E706650443291B5E1DF7EC928F7F4947BD958FA0E2C9585B6F6D36EA2B7294E0CA9EAC29301483045022100DF7D0F26B2DC92B6B0EB4C07E4AFEB48937D30B7E74448BAA90212A20E54BA5102200B2E7775AFAF0F44466F131D5FC07D98DF1EC1A485E8F1C5C617F9E441A4C11E41";
        final ByteArray payload = ByteArray.fromHexString(messageHeaderHexString + dsProofHexString);

        // Action
        final DoubleSpendProofMessage doubleSpendProofMessage = doubleSpendProofMessageInflater.fromBytes(payload.getBytes());
        final DoubleSpendProof doubleSpendProof = doubleSpendProofMessage.getDoubleSpendProof();

        // Assert
        Assert.assertEquals(expectedDoubleSpendProofHash, doubleSpendProof.getHash());
        Assert.assertEquals(ByteArray.fromHexString(dsProofHexString), doubleSpendProof.getBytes());
    }

    @Test
    public void should_inflate_and_deflate_double_spend_proof() throws Exception {
        // Setup
        final Sha256Hash expectedDoubleSpendProofHash = Sha256Hash.fromHexString("6E3A1BB3D65D62A1EBE78212583FCBF1108266C205E40874249007FD519B8E89");
        final TransactionOutputIdentifier transactionOutputIdentifierBeingDoubleSpent = new TransactionOutputIdentifier(Sha256Hash.fromHexString("00334930D2356064E43D376D998BE674301AC5ECEFD60D3CC2E261FF938137BB"), 0);

        final DoubleSpendProofPreimageInflater doubleSpendProofPreimageInflater = new DoubleSpendProofPreimageInflater();
        final DoubleSpendProofPreimage doubleSpendProofPreimage0 = doubleSpendProofPreimageInflater.fromBytes(ByteArray.fromHexString("02000000FFFFFFFF0000000007EFEE38FB0076EA7258F70D3E11B19F0636B1C998CA5D01F0FB9A3C7298A07D3BB13029CE7B1F559EF5E747FCAC439F1455A2EC7C5F09B72290795E7066504443D6A08791FB21CFCD84B904669E2877D1012211FFCDBA3EB14B562173C0646301473044022038045D988BB6BD9CF8616E8CD3B380D7AC358E71E2ADFB1F06BC7288544C9EAF02205C1F78F2FF04314A4E516D02D58897BCE120C2939950518D72BB2406F11FDC7C41"));
        final DoubleSpendProofPreimage doubleSpendProofPreimage1 = doubleSpendProofPreimageInflater.fromBytes(ByteArray.fromHexString("02000000FFFFFFFF0000000007EFEE38FB0076EA7258F70D3E11B19F0636B1C998CA5D01F0FB9A3C7298A07D3BB13029CE7B1F559EF5E747FCAC439F1455A2EC7C5F09B72290795E70665044F8CEDFF543DEA491E1A0CA101CE699BBD7AB45D6CBECBD9348C892B08752678801483045022100C7D72177641F40A8E5C6C168DCCE7A8EBC174667238856B30EE6657C04B68AE602205C281A9CA15E5292720A27C27AFF89F809B961C0D86A63C30CDCB06286658CDC41"));

        // Action
        final DoubleSpendProof doubleSpendProof = new DoubleSpendProof(transactionOutputIdentifierBeingDoubleSpent, doubleSpendProofPreimage0, doubleSpendProofPreimage1);

        // Assert
        Assert.assertEquals(expectedDoubleSpendProofHash, doubleSpendProof.getHash());
    }
}
