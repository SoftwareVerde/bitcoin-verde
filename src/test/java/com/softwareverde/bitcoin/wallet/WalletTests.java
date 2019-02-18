package com.softwareverde.bitcoin.wallet;

import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.secp256k1.key.PrivateKey;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
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

        final Wallet wallet = new Wallet();
        wallet.setSatoshisPerByteFee(1.5D);
        wallet.addPrivateKey(PrivateKey.fromHexString("FE1A361C9B70322E6797C1582B63327FDCB74808E16BCF319987BEE217CC37D4"));
        wallet.addTransaction(loadWalletTransaction0);
        wallet.addTransaction(loadWalletTransaction1);

        final MutableList<PaymentAmount> paymentAmounts = new MutableList<PaymentAmount>();
        paymentAmounts.add(new PaymentAmount(addressInflater.fromBase58Check("1PDBPJW7Ji7wgwQcbKFtqboDzHmyFoyLxq"), 831900L));

        // Action
        final Transaction signedTransaction = wallet.createTransaction(paymentAmounts);

        // Assert
        Assert.assertNotNull(signedTransaction);
    }
}
