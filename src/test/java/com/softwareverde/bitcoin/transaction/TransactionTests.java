package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import org.junit.Test;

public class TransactionTests {
    @Test
    public void should_calculate_transaction_hash_0() {
        // Setup
        final byte[] expectedTransactionBytes = BitcoinUtil.hexStringToByteArray("01000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D0134FFFFFFFF0100F2052A0100000043410411DB93E1DCDB8A016B49840F8C53BC1EB68A382E97B1482ECAD7B148A6909A5CB2E0EADDFB84CCF9744464F82E160BFA9B8B64F9D4C03F999B8643F656B412A3AC00000000");
        final byte[] expectedTransactionHash = BitcoinUtil.hexStringToByteArray("0437CD7F8525CEED2324359C2D0BA26006D92D856A9C20FA0241106EE5A597C9");

        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(expectedTransactionBytes);

        // Action
        final byte[] transactionBytes = transaction.getBytes();
        final byte[] transactionHash = transaction.calculateSha256Hash();

        // Assert
        TestUtil.assertEqual(expectedTransactionBytes, transactionBytes);
        TestUtil.assertEqual(expectedTransactionHash, transactionHash);
    }

    @Test
    public void should_calculate_transaction_hash_1() {
        // Setup
        final byte[] expectedTransactionBytes = BitcoinUtil.hexStringToByteArray("0100000001C997A5E56E104102FA209C6A852DD90660A20B2D9C352423EDCE25857FCD3704000000004847304402204E45E16932B8AF514961A1D3A1A25FDF3F4F7732E9D624C6C61548AB5FB8CD410220181522EC8ECA07DE4860A4ACDD12909D831CC56CBBAC4622082221A8768D1D0901FFFFFFFF0200CA9A3B00000000434104AE1A62FE09C5F51B13905F07F06B99A2F7159B2225F374CD378D71302FA28414E7AAB37397F554A7DF5F142C21C1B7303B8A0626F1BADED5C72A704F7E6CD84CAC00286BEE0000000043410411DB93E1DCDB8A016B49840F8C53BC1EB68A382E97B1482ECAD7B148A6909A5CB2E0EADDFB84CCF9744464F82E160BFA9B8B64F9D4C03F999B8643F656B412A3AC00000000");
        final byte[] expectedTransactionHash = BitcoinUtil.hexStringToByteArray("F4184FC596403B9D638783CF57ADFE4C75C605F6356FBC91338530E9831E9E16");

        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(expectedTransactionBytes);


        // Action
        final byte[] transactionBytes = transaction.getBytes();
        final byte[] transactionHash = transaction.calculateSha256Hash();

        // Assert
        TestUtil.assertEqual(expectedTransactionBytes, transactionBytes);
        TestUtil.assertEqual(expectedTransactionHash, transactionHash);
    }
}
