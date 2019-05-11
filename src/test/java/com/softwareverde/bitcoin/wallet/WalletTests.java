package com.softwareverde.bitcoin.wallet;

import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.secp256k1.key.PrivateKey;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.wallet.utxo.SpendableTransactionOutput;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

public class WalletTests {

    @Test
    public void should_sign_first_bitcoin_verde_transaction() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction loadWalletTransaction0 = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("0100000001DA69DE04948123F17F1CC0CF695C69A19F5D76AFFAC49E82BA3CD443289485B1000000006A473044022035ED503A1850C0E9DC7D14F3D1E106490B1E5FE62407931A2593E5EA5A9EEBCD0220556A7ED53B57300A0E6F0340649176274FF93965CBC66B44D4C8E6C9BCDA90FE412103FF15C531BCFBA5395C43FFB63E610B9509487DF90631595736C650C092076EC1FFFFFFFF02E7AF0C00000000001976A914008BB4D9A19DE9D31C93E191917663E40D45082388ACA6E58B00000000001976A914C780D70CE11F4CAFDC155F95F1B65188AE20D8C288AC00000000"));
        final Transaction loadWalletTransaction1 = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("010000000115840A9A95CD4A494A88B9D51D9B6A41DCEE646496AD68B94AB15654715035CB010000006B483045022100A83494B279DCA11717BCEC554B0ACFCC1D58B5F4A90236666CBFD8968DA8ADFC022019B60E4AF2E85B80DFF463920F13992E6311F682EFDFD3BDAC39DE2B722453EB41210335960D70AD4FF7E1845ACFF5E8B01937BD64141D706DB8AE4BFBE734959B2EE8FFFFFFFF0357040000000000001976A914008BB4D9A19DE9D31C93E191917663E40D45082388AC2C840000000000001976A91425F184D924A8DE349CFA556EE121443C3D2FB02688AC00000000000000007F6A076D656D6F7061794C7420436C61696D20757020746F20302E30303120667265652042434820666F7220796F75722031737420626574206F6E20424954434F494E20434153482047414D45532068747470733A2F2F7777772E6D656D6F7061792E78797A2F67615F6361736867616D65732E626974636F696E2E636F6D2000000000"));

        final PrivateKey privateKey = PrivateKey.fromHexString("FE1A361C9B70322E6797C1582B63327FDCB74808E16BCF319987BEE217CC37D4");

        final Wallet wallet = new Wallet();
        wallet.setSatoshisPerByteFee(1.5D);
        wallet.addPrivateKey(privateKey);
        wallet.addTransaction(loadWalletTransaction0);
        wallet.addTransaction(loadWalletTransaction1);

        final MutableList<PaymentAmount> paymentAmounts = new MutableList<PaymentAmount>();
        paymentAmounts.add(new PaymentAmount(addressInflater.fromBase58Check("1PDBPJW7Ji7wgwQcbKFtqboDzHmyFoyLxq"), 831900L));

        // Action
        final Transaction signedTransaction = wallet.createTransaction(paymentAmounts, null);

        // Assert
        Assert.assertNotNull(signedTransaction);
    }

    @Test
    public void should_suggest_spending_two_outputs_when_amount_plus_fee_is_2_satoshis_greater_than_send_amount() {
        // Setup
        final Wallet wallet = new Wallet();
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction0 = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("020000000356D78B2C132C2AC6E3750261FE354F580A5DBDE342B84C6AFD215DEBD3C422EF000000008B483045022100EE3FCFB81BC42DAC7B3C609778E0FFBEB8DBFBF716AF683A969CF06E7420273C02204497E110CC05A3938A4DD7D196E28552FFA6A45062D41A68B7D69AB2F50586974141048D96DBA2459207CE3E07680AC326A1908763D3C22E1635EFBDEC851DCA52C23CB4AA21A1EE9AC97B53769935A2C33D10006768813C6039F38BBBA0DA3BB10CE9FFFFFFFF55A0691E4ED19B62950AFB44ED5752D1A84EDB24300E73CE84E9DCA83A3D3900000000006A473044022006CFD58EB7FE758401A4533D80EF39C75B115D485F50D9CF2A0D9861FA6371EE022030CD683FE4E731199C9FF81BD9943B76142BC386A56F4A24D31EC08C0467F2AF4121029AEABFB7D24D3360C965EC4C0732357657EE4183660BB0BC050C0DE22086F53CFFFFFFFF5FE55EB0BCB9D5DD5BB601E114F400FB944EDB0F5D6FB994A0F6F06E047897DE010000008A473044022019214E1FA525DB7B944251CE22B59E40E62F5FDECB8487B424277E15ACE0F138022019472F5BF5D94BE68B00552BE3C36129430A20EBE4DC2625EF18D5B0471DED3E4141048D96DBA2459207CE3E07680AC326A1908763D3C22E1635EFBDEC851DCA52C23CB4AA21A1EE9AC97B53769935A2C33D10006768813C6039F38BBBA0DA3BB10CE9FFFFFFFF0166110000000000001976A914FFA1B9D778854241598DFC229D0518A07562CE8888AC00000000"));
        final Transaction transaction1 = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("02000000012856893C183BE172DD9B2F16AA6776F010393D497EFA80CB8FD85E1418E52965000000008B4830450221009478F83666802E39F64AF21CD737BE4E40B85F0C0D330AF122D1B941B8B901EA02201678D4B342F23B89E47C14321F4F712396FCFB6EC61B84AFDA0E087B39045DD64141049602CA68608F9AB02DC2DA445C97F2D3980F75A5C620742C309BDD8B7E5A5B640FD0D5AF4A8168711728B672F2AE0675CFD4E7B67F7EC1E5C6F2EB4BD2E61782FFFFFFFF0268420000000000001976A914FC2B2B044B55604B87AFFFDEDB4C7C581C76728688AC30290000000000001976A91455CE63AE0472184AE53B26BB117D6843C667BA3388AC00000000"));

        final PrivateKey privateKey0 = PrivateKey.fromHexString("01D16F62F788E9AF42048A55952DCA9C308D5A0D81236EA3B06970370DC1E5E8");
        final PrivateKey privateKey1 = PrivateKey.fromHexString("7BC4097C60CAC40D36A17E5275198CA0A971884D233B0F022F9036B8720A0A9F");

        wallet.addPrivateKey(privateKey0);
        wallet.addPrivateKey(privateKey1);

        wallet.addTransaction(transaction0);
        wallet.addTransaction(transaction1);

        wallet.setSatoshisPerByteFee(2D);

        final long calculatedFees = wallet.calculateFees(2, 1);
        Assert.assertEquals(452L, calculatedFees);

        final TransactionOutputIdentifier almostSufficientOutputIdentifier = new TransactionOutputIdentifier(Sha256Hash.fromHexString("3B04F22BCCEFED1074563B6093F6C9D1C00125571AF62BAD8ACFFEAD46E3B3AF"), 0);
        final SpendableTransactionOutput almostSufficientOutput = wallet.getTransactionOutput(almostSufficientOutputIdentifier);
        Assert.assertEquals(Long.valueOf(4454L), almostSufficientOutput.getTransactionOutput().getAmount());

        final TransactionOutputIdentifier completelySufficientOutputIdentifier = new TransactionOutputIdentifier(Sha256Hash.fromHexString("10D161CD11F413042FBF9CA74C5293B45486DD0747D93C9EFC56F483C9E03621"), 1);
        final SpendableTransactionOutput completelySufficientOutput = wallet.getTransactionOutput(completelySufficientOutputIdentifier);
        Assert.assertEquals(Long.valueOf(10544L), completelySufficientOutput.getTransactionOutput().getAmount());

        Assert.assertEquals(2L, wallet.getTransactionOutputs().getSize());

        // Action
        final List<TransactionOutputIdentifier> outputsToSpend = wallet.getOutputsToSpend(2, 4000L); // Should select the second input since it covers ths whole transaction and the first output is 2 sats shy of paying the amount + fees.

        // Assert
        Assert.assertEquals(1, outputsToSpend.getSize());

        final TransactionOutputIdentifier transactionOutputIdentifier = outputsToSpend.get(0);
        Assert.assertEquals(completelySufficientOutputIdentifier, transactionOutputIdentifier);
    }
}
