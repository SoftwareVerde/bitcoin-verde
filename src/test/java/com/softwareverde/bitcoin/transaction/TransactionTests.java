package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.bitcoin.type.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.IoUtil;
import org.junit.Assert;
import org.junit.Test;

public class TransactionTests {
    @Test
    public void should_calculate_transaction_hash_0() {
        // Setup
        final TransactionDeflater transactionDeflater = new TransactionDeflater();

        final byte[] expectedTransactionBytes = HexUtil.hexStringToByteArray("01000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D0134FFFFFFFF0100F2052A0100000043410411DB93E1DCDB8A016B49840F8C53BC1EB68A382E97B1482ECAD7B148A6909A5CB2E0EADDFB84CCF9744464F82E160BFA9B8B64F9D4C03F999B8643F656B412A3AC00000000");
        final byte[] expectedTransactionHash = HexUtil.hexStringToByteArray("0437CD7F8525CEED2324359C2D0BA26006D92D856A9C20FA0241106EE5A597C9");

        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(expectedTransactionBytes);

        // Action
        final byte[] transactionBytes = transactionDeflater.toBytes(transaction).getBytes();
        final Sha256Hash transactionHash = transaction.getHash();

        // Assert
        TestUtil.assertEqual(expectedTransactionBytes, transactionBytes);
        TestUtil.assertEqual(expectedTransactionHash, transactionHash.getBytes());
    }

    @Test
    public void should_calculate_transaction_hash_1() {
        // Setup
        final TransactionDeflater transactionDeflater = new TransactionDeflater();

        final byte[] expectedTransactionBytes = HexUtil.hexStringToByteArray("0100000001C997A5E56E104102FA209C6A852DD90660A20B2D9C352423EDCE25857FCD3704000000004847304402204E45E16932B8AF514961A1D3A1A25FDF3F4F7732E9D624C6C61548AB5FB8CD410220181522EC8ECA07DE4860A4ACDD12909D831CC56CBBAC4622082221A8768D1D0901FFFFFFFF0200CA9A3B00000000434104AE1A62FE09C5F51B13905F07F06B99A2F7159B2225F374CD378D71302FA28414E7AAB37397F554A7DF5F142C21C1B7303B8A0626F1BADED5C72A704F7E6CD84CAC00286BEE0000000043410411DB93E1DCDB8A016B49840F8C53BC1EB68A382E97B1482ECAD7B148A6909A5CB2E0EADDFB84CCF9744464F82E160BFA9B8B64F9D4C03F999B8643F656B412A3AC00000000");
        final byte[] expectedTransactionHash = HexUtil.hexStringToByteArray("F4184FC596403B9D638783CF57ADFE4C75C605F6356FBC91338530E9831E9E16");

        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(expectedTransactionBytes);


        // Action
        final byte[] transactionBytes = transactionDeflater.toBytes(transaction).getBytes();
        final Sha256Hash transactionHash = transaction.getHash();

        // Assert
        TestUtil.assertEqual(expectedTransactionBytes, transactionBytes);
        TestUtil.assertEqual(expectedTransactionHash, transactionHash.getBytes());
    }

    @Test
    public void bug_0001_should_calculate_hash_for_block_29664() {
        // Setup
        final BlockInflater blockInflater = new BlockInflater();
        final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(IoUtil.getResource("/blocks/00000000AFE94C578B4DC327AA64E1203283C5FD5F152CE886341766298CF523")));
        final Transaction transaction = block.getTransactions().get(1);

        final Sha256Hash expectedTransactionHash = MutableSha256Hash.fromHexString("3A5769FB2126D870ADED5FCACED3BC49FA9768436101895931ADB5246E41E957");
        final int expectedInputCount = 320;
        final int expectedOutputCount = 1;

        // Action
        final Sha256Hash transactionHash = transaction.getHash();

        // Assert
        Assert.assertEquals(expectedInputCount, transaction.getTransactionInputs().getSize());
        Assert.assertEquals(expectedOutputCount, transaction.getTransactionOutputs().getSize());
        Assert.assertEquals(transactionHash, expectedTransactionHash);
    }
}
