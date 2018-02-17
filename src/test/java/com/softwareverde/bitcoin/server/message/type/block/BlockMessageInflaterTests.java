package com.softwareverde.bitcoin.server.message.type.block;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class BlockMessageInflaterTests {

    @Test
    public void should_inflate_genesis_block_message_from_byte_array() {
        // Setup
        // E3E1 F3E8 626C 6F63 6B00 0000 0000 0000 1D01 0000 F71A 2403
        final byte[] genesisBlockMessageBytes = BitcoinUtil.hexStringToByteArray("E3E1F3E8626C6F636B000000000000001D010000F71A24030100000000000000000000000000000000000000000000000000000000000000000000003BA3EDFD7A7B12B27AC72C3E67768F617FC81BC3888A51323A9FB8AA4B1E5E4A29AB5F49FFFF001D1DAC2B7C0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF4D04FFFF001D0104455468652054696D65732030332F4A616E2F32303039204368616E63656C6C6F72206F6E206272696E6B206F66207365636F6E64206261696C6F757420666F722062616E6B73FFFFFFFF0100F2052A01000000434104678AFDB0FE5548271967F1A67130B7105CD6A828E03909A67962E0EA1F61DEB649F6BC3F4CEF38C4F35504E51EC112DE5C384DF7BA0B8D578A4C702B6BF11D5FAC00000000");

        final BlockMessageInflater blockMessageInflater = new BlockMessageInflater();

        // Action
        final BlockMessage blockMessage = blockMessageInflater.fromBytes(genesisBlockMessageBytes);

        // Assert
        final Block block = blockMessage.getBlock();
        Assert.assertNotNull(block);

        TestUtil.assertEqual(BitcoinUtil.hexStringToByteArray("000000000019D6689C085AE165831E934FF763AE46A2A6C172B3F1B60A8CE26F"), block.calculateSha256Hash());
        Assert.assertTrue(block.getDifficulty().isSatisfiedBy(block.calculateSha256Hash()));

        Assert.assertEquals(1, block.getVersion().intValue());
        TestUtil.assertEqual(new byte[]{ }, block.getPreviousBlockHash());
        TestUtil.assertEqual(BitcoinUtil.hexStringToByteArray("4A5E1E4BAAB89F3A32518A88C31BC87F618F76673E2CC77AB2127B7AFDEDA33B"), block.getMerkleRoot());
        Assert.assertEquals(1231006505L, block.getTimestamp().longValue());
        Assert.assertEquals(1.0, Math.round(block.getDifficulty().getDifficultyRatio().doubleValue() * 100.0) / 100.0, 0.0001);
        Assert.assertEquals(2083236893L, block.getNonce().longValue());

        final List<Transaction> transactions = block.getTransactions();
        Assert.assertEquals(1, transactions.size());

        final Transaction transaction = transactions.get(0);
        Assert.assertEquals(1, transaction.getVersion().intValue());
        Assert.assertEquals(false, transaction.hasWitnessData());
        Assert.assertEquals(0L, transaction.getLockTime().getTimestamp().longValue());

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        Assert.assertEquals(1, transactionInputs.size());
        final TransactionInput transactionInput = transactionInputs.get(0);
        TestUtil.assertEqual(BitcoinUtil.hexStringToByteArray("0000000000000000000000000000000000000000000000000000000000000000"), transactionInput.getPreviousTransactionOutput());
        Assert.assertEquals(0xFFFFFFFF, transactionInput.getPreviousTransactionOutputIndex().intValue());
        TestUtil.assertEqual(BitcoinUtil.hexStringToByteArray("736B6E616220726F662074756F6C69616220646E6F63657320666F206B6E697262206E6F20726F6C6C65636E61684320393030322F6E614A2F33302073656D6954206568544504011D00FFFF04"), transactionInput.getSignatureScript());
        Assert.assertEquals(0xFFFFFFFF, transactionInput.getSequenceNumber().intValue());

        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        Assert.assertEquals(1, transactionOutputs.size());
        final TransactionOutput transactionOutput = transactionOutputs.get(0);
        Assert.assertEquals(5000000000L, transactionOutput.getValue().longValue());
        TestUtil.assertEqual(BitcoinUtil.hexStringToByteArray("AC5F1DF16B2B704C8A578D0BBAF74D385CDE12C11EE50455F3C438EF4C3FBCF649B6DE611FEAE06279A60939E028A8D65C10B73071A6F16719274855FEB0FD8A670441"), transactionOutput.getScript());
    }
}
