package com.softwareverde.bitcoin.server.message.type.block;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.constable.list.List;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

public class BlockMessageInflaterTests {

    @Test
    public void should_inflate_genesis_block_message_from_byte_array() {
        // Setup
        // E3E1 F3E8 626C 6F63 6B00 0000 0000 0000 1D01 0000 F71A 2403
        final byte[] genesisBlockMessageBytes = HexUtil.hexStringToByteArray("E3E1F3E8626C6F636B000000000000001D010000F71A24030100000000000000000000000000000000000000000000000000000000000000000000003BA3EDFD7A7B12B27AC72C3E67768F617FC81BC3888A51323A9FB8AA4B1E5E4A29AB5F49FFFF001D1DAC2B7C0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF4D04FFFF001D0104455468652054696D65732030332F4A616E2F32303039204368616E63656C6C6F72206F6E206272696E6B206F66207365636F6E64206261696C6F757420666F722062616E6B73FFFFFFFF0100F2052A01000000434104678AFDB0FE5548271967F1A67130B7105CD6A828E03909A67962E0EA1F61DEB649F6BC3F4CEF38C4F35504E51EC112DE5C384DF7BA0B8D578A4C702B6BF11D5FAC00000000");

        final BlockMessageInflater blockMessageInflater = new BlockMessageInflater();

        // Action
        final BlockMessage blockMessage = blockMessageInflater.fromBytes(genesisBlockMessageBytes);

        // Assert
        final Block block = blockMessage.getBlock();
        Assert.assertNotNull(block);

        TestUtil.assertEqual(HexUtil.hexStringToByteArray("000000000019D6689C085AE165831E934FF763AE46A2A6C172B3F1B60A8CE26F"), block.getHash().getBytes());
        Assert.assertTrue(block.getDifficulty().isSatisfiedBy(block.getHash()));

        Assert.assertEquals(1, block.getVersion().intValue());
        TestUtil.assertEqual(new byte[]{ }, block.getPreviousBlockHash().getBytes());
        TestUtil.assertEqual(HexUtil.hexStringToByteArray("4A5E1E4BAAB89F3A32518A88C31BC87F618F76673E2CC77AB2127B7AFDEDA33B"), block.getMerkleRoot().getBytes());
        Assert.assertEquals(1231006505L, block.getTimestamp().longValue());
        Assert.assertEquals(1.0, Math.round(block.getDifficulty().getDifficultyRatio().doubleValue() * 100.0) / 100.0, 0.0001);
        Assert.assertEquals(2083236893L, block.getNonce().longValue());

        final List<Transaction> transactions = block.getTransactions();
        Assert.assertEquals(1, transactions.getSize());

        final Transaction transaction = transactions.get(0);
        Assert.assertEquals(1, transaction.getVersion().intValue());
        Assert.assertEquals(0L, transaction.getLockTime().getValue().longValue());

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        Assert.assertEquals(1, transactionInputs.getSize());
        final TransactionInput transactionInput = transactionInputs.get(0);
        TestUtil.assertEqual(HexUtil.hexStringToByteArray("0000000000000000000000000000000000000000000000000000000000000000"), transactionInput.getPreviousOutputTransactionHash().getBytes());
        Assert.assertEquals(0xFFFFFFFF, transactionInput.getPreviousOutputIndex().intValue());
        TestUtil.assertEqual(HexUtil.hexStringToByteArray("04FFFF001D0104455468652054696D65732030332F4A616E2F32303039204368616E63656C6C6F72206F6E206272696E6B206F66207365636F6E64206261696C6F757420666F722062616E6B73"), transactionInput.getUnlockingScript().getBytes().getBytes());
        Assert.assertEquals(0xFFFFFFFF, transactionInput.getSequenceNumber().intValue());

        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        Assert.assertEquals(1, transactionOutputs.getSize());
        final TransactionOutput transactionOutput = transactionOutputs.get(0);
        Assert.assertEquals(5000000000L, transactionOutput.getAmount().longValue());
        TestUtil.assertEqual(HexUtil.hexStringToByteArray("4104678AFDB0FE5548271967F1A67130B7105CD6A828E03909A67962E0EA1F61DEB649F6BC3F4CEF38C4F35504E51EC112DE5C384DF7BA0B8D578A4C702B6BF11D5FAC"), transactionOutput.getLockingScript().getBytes().getBytes());
    }
}
