package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.header.MutableBlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.ImmutableDifficulty;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.ImmutableMedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MedianBlockTimeWithBlocks;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTimeTests;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.security.secp256k1.key.PrivateKey;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.ReadUncommittedDatabaseConnectionFactoryWrapper;
import com.softwareverde.bitcoin.server.database.cache.LocalDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.MasterDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.MasterDatabaseManagerCacheCore;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.database.wrapper.MysqlDatabaseConnectionWrapper;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.test.TransactionTestUtil;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.signer.*;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorFactory;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorTests;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.connection.ReadUncommittedDatabaseConnectionFactory;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.time.ImmutableNetworkTime;
import com.softwareverde.util.DateUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.IoUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

public class BlockValidatorTests extends IntegrationTest {
    final PrivateKey _privateKey = PrivateKey.fromHexString("2F9DFE0F574973D008DA9A98D1D39422D044154E2008E195643AD026F1B2B554");

    public static class FakeMedianBlockTime implements MedianBlockTimeWithBlocks {

        @Override
        public MedianBlockTime subset(final Integer blockCount) {
            return new ImmutableMedianBlockTime(Long.MAX_VALUE);
        }

        @Override
        public BlockHeader getBlockHeader(final Integer indexFromTip) {
            return new MutableMedianBlockTimeTests.FakeBlockHeader(Long.MAX_VALUE);
        }

        @Override
        public ImmutableMedianBlockTime asConst() {
            return new ImmutableMedianBlockTime(Long.MAX_VALUE);
        }

        @Override
        public Long getCurrentTimeInSeconds() {
            return Long.MAX_VALUE;
        }

        @Override
        public Long getCurrentTimeInMilliSeconds() {
            return Long.MAX_VALUE;
        }
    }

    private void _storeBlocks(final int blockCount, final Long timestamp) throws Exception {
        final TransactionInflater transactionInflater = new TransactionInflater();
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockInflater blockInflater = new BlockInflater();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            for (int i = 0; i < blockCount; ++i) {
                final Sha256Hash mostRecentBlockHash = blockHeaderDatabaseManager.getHeadBlockHeaderHash();
                MutableBlock block;

                if (mostRecentBlockHash == null) {
                    final String genesisBlockData = IoUtil.getResource("/blocks/" + HexUtil.toHexString(BlockHeader.GENESIS_BLOCK_HASH.getBytes()));
                    block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(genesisBlockData));
                }
                else {
                    final Long blockHeight = null;
                    final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(mostRecentBlockHash);
                    final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(blockId);
                    final ImmutableListBuilder<Transaction> listBuilder = new ImmutableListBuilder<Transaction>(1);
                    final AddressInflater addressInflater = new AddressInflater();
                    listBuilder.add(transactionInflater.createCoinbaseTransaction(blockHeight, "Fake Block", addressInflater.fromPrivateKey(_privateKey), 50 * Transaction.SATOSHIS_PER_BITCOIN));
                    block = new MutableBlock(blockHeader, listBuilder.build());

                    block.setPreviousBlockHash(mostRecentBlockHash);
                    block.setNonce(block.getNonce() + 1);
                    block.setTimestamp(timestamp + i);
                }

                blockDatabaseManager.insertBlock(block);
            }
        }
    }

    @Before
    public void setup() {
        _resetDatabase();
        MonitoredDatabaseConnection.databaseConnectionCount.set(0);
    }

    @Test
    public void should_validate_block_that_contains_transaction_that_spends_outputs_in_same_block() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockInflater blockInflater = new BlockInflater();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final TransactionValidatorFactory transactionValidatorFactory = new TransactionValidatorFactory();
            final BlockValidator blockValidator = new BlockValidator(_readUncomittedDatabaseManagerFactory, transactionValidatorFactory, new ImmutableNetworkTime(Long.MAX_VALUE), new FakeMedianBlockTime());

            { // Store the blocks and transactions included within the block-under-test so that it should appear valid...
                final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockDatabaseManager.insertBlock(genesisBlock);
                }

                { // Store the block header before the prerequisite block so that it may be inflated by the difficultyCalculator. Block Hash: 0000000009B905F3DB4990848B9B14FE39582147FCF6A6CB8D73BD227D75369E
                    final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
                    final BlockHeader templateBlockHeader = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0100000042490A3DE212575A7CADE89BED6AC18A4466E667F3D679BC1DA1B2BD00000000EDB0A433B741049D6E7C0B44838A188AD809C60FFE5076F4C881CB93AB70B247ECC66E49FFFF001D06FAFE08"));
                    final BlockHeader blockHeader = new MutableBlockHeader(templateBlockHeader) {
                        @Override
                        public Sha256Hash getPreviousBlockHash() {
                            return BlockHeader.GENESIS_BLOCK_HASH;
                        }
                        @Override
                        public Sha256Hash getHash() {
                            return templateBlockHeader.getHash();
                        }
                    };
                    synchronized (BlockHeaderDatabaseManager.MUTEX) {
                        blockHeaderDatabaseManager.storeBlockHeader(blockHeader);
                    }
                }

                // Block Hash: 000000002D947997DC957CDF075DD32390F5F754D2656208D5DD82A6620179F5
                final Block previousPrerequisiteBlock1 = blockInflater.fromBytes(HexUtil.hexStringToByteArray("010000009E36757D22BD738DCBA6F6FC47215839FE149B8B849049DBF305B90900000000E398331A75C87C42E14D571DFA7EF036CF4C06F85D05FEE2D366BB2ACC1B1FD4A5C96E49FFFF001D02B465C10101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0804FFFF001D027705FFFFFFFF0100F2052A01000000434104BA8220A0CDE503EE7F923AB223B07C22E705FB5215E1A3F5E6DFB37CF8A714D0D6D9F2A7A00A61675CF20ABA71233973D1DEA913C130F0AF380ED4A9C2116045AC00000000"));
                final BlockId previousPrerequisiteBlock1Id;
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    previousPrerequisiteBlock1Id = blockDatabaseManager.insertBlock(previousPrerequisiteBlock1); // This block must be stored so that the prerequisiteBlock will have the correct hash (without this block, prerequisiteBlock's previous_block value is zeroed).
                }
                final BlockchainSegmentId firstBlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(previousPrerequisiteBlock1Id);

                // Block Hash: 00000000689051C09FF2CD091CC4C22C10B965EB8DB3AD5F032621CC36626175
                final Block prerequisiteBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray("01000000F5790162A682DDD5086265D254F7F59023D35D07DF7C95DC9779942D00000000193028D8B78007269D52B2A1068E32EDD21D0772C2C157954F7174761B78A51A30CE6E49FFFF001D3A2E34480201000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0804FFFF001D027C05FFFFFFFF0100F2052A01000000434104B43BB206B71F34E2FAB9359B156FF683BED889021A06C315722A7C936B9743AD88A8882DC13EECAFCDAD4F082D2D0CC54AA177204F79DC7305F1F4857B7B8802AC00000000010000000177B5E6E78F8552129D07A73801B1A5F6830EC040D218755B46340B4CF6D21FD7000000004A49304602210083EC8BD391269F00F3D714A54F4DBD6B8004B3E9C91F3078FF4FCA42DA456F4D0221008DFE1450870A717F59A494B77B36B7884381233555F8439DAC4EA969977DD3F401FFFFFFFF0200E1F505000000004341044A656F065871A353F216CA26CEF8DDE2F03E8C16202D2E8AD769F02032CB86A5EB5E56842E92E19141D60A01928F8DD2C875A390F67C1F6C94CFC617C0EA45AFAC00180D8F00000000434104F36C67039006EC4ED2C885D7AB0763FEB5DEB9633CF63841474712E4CF0459356750185FC9D962D0F4A1E08E1A84F0C9A9F826AD067675403C19D752530492DCAC00000000"));
                boolean isCoinbase = true;
                for (final Transaction transaction : prerequisiteBlock.getTransactions()) {
                    if (! isCoinbase) {
                        TransactionTestUtil.createRequiredTransactionInputs(databaseManager, firstBlockchainSegmentId, transaction);
                    }
                    isCoinbase = false;
                }
                final BlockId prerequisiteBlockId;
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    prerequisiteBlockId = blockDatabaseManager.insertBlock(prerequisiteBlock);
                }
                final Boolean prerequisiteBlockIsValid = blockValidator.validateBlock(prerequisiteBlockId, prerequisiteBlock).isValid;
                Assert.assertTrue(prerequisiteBlockIsValid);
            }

            // Block Hash: 000000005A4DED781E667E06CEEFAFB71410B511FE0D5ADC3E5A27ECBEC34AE6
            final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray("0100000075616236CC2126035FADB38DEB65B9102CC2C41C09CDF29FC051906800000000FE7D5E12EF0FF901F6050211249919B1C0653771832B3A80C66CEA42847F0AE1D4D26E49FFFF001D00F0A4410401000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0804FFFF001D029105FFFFFFFF0100F2052A010000004341046D8709A041D34357697DFCB30A9D05900A6294078012BF3BB09C6F9B525F1D16D5503D7905DB1ADA9501446EA00728668FC5719AA80BE2FDFC8A858A4DBDD4FBAC00000000010000000255605DC6F5C3DC148B6DA58442B0B2CD422BE385EAB2EBEA4119EE9C268D28350000000049483045022100AA46504BAA86DF8A33B1192B1B9367B4D729DC41E389F2C04F3E5C7F0559AAE702205E82253A54BF5C4F65B7428551554B2045167D6D206DFE6A2E198127D3F7DF1501FFFFFFFF55605DC6F5C3DC148B6DA58442B0B2CD422BE385EAB2EBEA4119EE9C268D2835010000004847304402202329484C35FA9D6BB32A55A70C0982F606CE0E3634B69006138683BCD12CBB6602200C28FEB1E2555C3210F1DDDB299738B4FF8BBE9667B68CB8764B5AC17B7ADF0001FFFFFFFF0200E1F505000000004341046A0765B5865641CE08DD39690AADE26DFBF5511430CA428A3089261361CEF170E3929A68AEE3D8D4848B0C5111B0A37B82B86AD559FD2A745B44D8E8D9DFDC0CAC00180D8F000000004341044A656F065871A353F216CA26CEF8DDE2F03E8C16202D2E8AD769F02032CB86A5EB5E56842E92E19141D60A01928F8DD2C875A390F67C1F6C94CFC617C0EA45AFAC0000000001000000025F9A06D3ACDCEB56BE1BFEAA3E8A25E62D182FA24FEFE899D1C17F1DAD4C2028000000004847304402205D6058484157235B06028C30736C15613A28BDB768EE628094CA8B0030D4D6EB0220328789C9A2EC27DDAEC0AD5EF58EFDED42E6EA17C2E1CE838F3D6913F5E95DB601FFFFFFFF5F9A06D3ACDCEB56BE1BFEAA3E8A25E62D182FA24FEFE899D1C17F1DAD4C2028010000004A493046022100C45AF050D3CEA806CEDD0AB22520C53EBE63B987B8954146CDCA42487B84BDD6022100B9B027716A6B59E640DA50A864D6DD8A0EF24C76CE62391FA3EABAF4D2886D2D01FFFFFFFF0200E1F505000000004341046A0765B5865641CE08DD39690AADE26DFBF5511430CA428A3089261361CEF170E3929A68AEE3D8D4848B0C5111B0A37B82B86AD559FD2A745B44D8E8D9DFDC0CAC00180D8F000000004341046A0765B5865641CE08DD39690AADE26DFBF5511430CA428A3089261361CEF170E3929A68AEE3D8D4848B0C5111B0A37B82B86AD559FD2A745B44D8E8D9DFDC0CAC000000000100000002E2274E5FEA1BF29D963914BD301AA63B64DAAF8A3E88F119B5046CA5738A0F6B0000000048473044022016E7A727A061EA2254A6C358376AAA617AC537EB836C77D646EBDA4C748AAC8B0220192CE28BF9F2C06A6467E6531E27648D2B3E2E2BAE85159C9242939840295BA501FFFFFFFFE2274E5FEA1BF29D963914BD301AA63B64DAAF8A3E88F119B5046CA5738A0F6B010000004A493046022100B7A1A755588D4190118936E15CD217D133B0E4A53C3C15924010D5648D8925C9022100AAEF031874DB2114F2D869AC2DE4AE53908FBFEA5B2B1862E181626BB9005C9F01FFFFFFFF0200E1F505000000004341044A656F065871A353F216CA26CEF8DDE2F03E8C16202D2E8AD769F02032CB86A5EB5E56842E92E19141D60A01928F8DD2C875A390F67C1F6C94CFC617C0EA45AFAC00180D8F000000004341046A0765B5865641CE08DD39690AADE26DFBF5511430CA428A3089261361CEF170E3929A68AEE3D8D4848B0C5111B0A37B82B86AD559FD2A745B44D8E8D9DFDC0CAC00000000"));
            final BlockId blockId;
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockId = blockDatabaseManager.insertBlock(block);
            }

            // Action
            final Boolean blockIsValid = blockValidator.validateBlock(blockId, block).isValid;

            // Assert
            Assert.assertTrue(blockIsValid);
        }
    }

    // Test disabled on 2019-02-16.
    //   The following Test is disabled because the test block (ForkChain4.BLOCK_1) was mined with an invalid coinbase TransactionInput (prevoutIndex is 0, not -1).
    //   To re-enable this test, a block must be mined that spends its own coinbase.
    //   Mining this custom block cannot be done via Stratum since Stratum mutates the coinbase transaction hash via extraNonce2.  Instead, the CPU miner
    //   must be used.
    // @Test
    // public void should_validate_transactions_that_spend_its_coinbase() throws Exception {
    //     // NOTE: Spending the coinbase transaction within 100 blocks of its mining is invalid.
    //
    //     try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
    //         // Setup
    //         final CoreBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
    //
    //         final BlockInflater blockInflater = new BlockInflater();
    //         final BlockValidator blockValidator = new BlockValidator(_readUncomittedDatabaseManagerFactory, new ImmutableNetworkTime(Long.MAX_VALUE), new FakeMedianBlockTime());
    //
    //         final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
    //         synchronized (BlockHeaderDatabaseManager.MUTEX) {
    //             final BlockId genesisBlockId = blockDatabaseManager.insertBlock(genesisBlock);
    //         }
    //
    //         final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain4.BLOCK_1));
    //         final BlockId blockId;
    //         synchronized (BlockHeaderDatabaseManager.MUTEX) {
    //             blockId = blockDatabaseManager.insertBlock(block);
    //         }
    //
    //         // Action
    //         final BlockValidationResult blockIsValid = blockValidator.validateBlock(blockId, block);
    //
    //         // Assert
    //         Assert.assertTrue(blockIsValid.isValid);
    //     }
    // }

    @Test
    public void block_should_be_invalid_if_its_input_only_exists_within_a_different_chain() throws Exception {
        // Setup

        /*
            // Given the following forking situation...

            [2']                    // [2'] attempts to spend {UTXO-A}, [2'] does NOT include {UTXO-A}.
             |
            [1']  [1'']             // [1''] includes {UTXO-A}, [1'] does NOT include {UTXO-A}.
             |     |
            [0]----+
             |

            //
        */

        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            final BlockInflater blockInflater = new BlockInflater();
            final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));

            final Block block1Prime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain2.BLOCK_1));
            Assert.assertEquals(genesisBlock.getHash(), block1Prime.getPreviousBlockHash());

            final Block block2Prime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain2.INVALID_BLOCK_2)); // Spends a transaction within block1DoublePrime...
            Assert.assertEquals(block1Prime.getHash(), block2Prime.getPreviousBlockHash());

            final Block block1DoublePrime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain5.BLOCK_1));
            Assert.assertEquals(genesisBlock.getHash(), block1DoublePrime.getPreviousBlockHash());

            final TransactionValidatorFactory transactionValidatorFactory = new TransactionValidatorFactory();

            final BlockValidator blockValidator = new BlockValidator(_readUncomittedDatabaseManagerFactory, transactionValidatorFactory, new ImmutableNetworkTime(Long.MAX_VALUE), new FakeMedianBlockTime());

            final BlockchainSegmentId genesisBlockchainSegmentId;
            {
                final BlockId genesisBlockId;
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    genesisBlockId = blockDatabaseManager.insertBlock(genesisBlock);
                }
                genesisBlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(genesisBlockId);
                // Assert.assertTrue(blockValidator.validateBlock(genesisBlockchainSegmentId, genesisBlock)); // NOTE: This assertion is disabled for the genesis block. (The difficulty calculation for this block fails, but it's the genesis block, so it's likely not applicable.)
            }

            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                final BlockId block1PrimeId = blockDatabaseManager.insertBlock(block1Prime);
                Assert.assertTrue(blockValidator.validateBlock(block1PrimeId, block1Prime).isValid);
            }

            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                final BlockId block1DoublePrimeId = blockDatabaseManager.insertBlock(block1DoublePrime);
                Assert.assertTrue(blockValidator.validateBlock(block1DoublePrimeId, block1DoublePrime).isValid);
            }

            // TransactionInput 0:4A23572C0048299E956AE25262B3C3E75D984A0CCE36B9C60E9A741E14E099F7 should exist within the database, however, it should exist only within a separate chain...
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT transaction_outputs.id FROM transaction_outputs INNER JOIN transactions ON transactions.id = transaction_outputs.transaction_id WHERE transactions.hash = ? AND transaction_outputs.`index` = ?")
                    .setParameter("4A23572C0048299E956AE25262B3C3E75D984A0CCE36B9C60E9A741E14E099F7")
                    .setParameter("0")
            );
            Assert.assertTrue(rows.size() > 0);

            // Action
            Boolean block2PrimeIsValid;
            try {
                final BlockId block2Id;
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    block2Id = blockDatabaseManager.insertBlock(block2Prime);
                }
                block2PrimeIsValid = blockValidator.validateBlock(block2Id, block2Prime).isValid;
            }
            catch (final DatabaseException exception) {
                block2PrimeIsValid = false;
            }

            // Assert
            Assert.assertFalse(block2PrimeIsValid);
        }
    }

    @Test
    public void difficulty_should_be_recalculated_every_2016th_block() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final TimeZone timeZone = TimeZone.getTimeZone("GMT+1:00"); // Rome, Italy; DST
            final long blockHeight = 2016 * 2; // 32256L;

            final Long genesisBlockTimestamp = (DateUtil.datetimeToTimestamp("2009-01-03 18:15:05", timeZone) / 1000L);

            final BlockInflater blockInflater = new BlockInflater();
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final TransactionValidatorFactory transactionValidatorFactory = new TransactionValidatorFactory();
            final BlockValidator blockValidator = new BlockValidator(_readUncomittedDatabaseManagerFactory, transactionValidatorFactory, new ImmutableNetworkTime(Long.MAX_VALUE), new FakeMedianBlockTime());

            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                _storeBlocks(1, genesisBlockTimestamp); // Store the genesis block... (Since the genesis-block is considered block-height 0.)
            }

            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                // _storeBlocks(2015, genesisBlockTimestamp + 1L);
                _storeBlocks(1, genesisBlockTimestamp + 1L);
                databaseConnection.executeSql(
                    new Query("UPDATE blocks SET block_height = ? WHERE id = ?")
                        .setParameter(2015)
                        .setParameter(blockHeaderDatabaseManager.getHeadBlockHeaderId())
                );
            }

            { // Store the block that is 2016 blocks before the firstBlockWithDifficultyAdjustment
                // Block Hash: 000000000FA8BFA0F0DD32F956B874B2C7F1772C5FBEDCB1B35E03335C7FB0A8
                // Block Height: 30240
                // Timestamp: B1512B4B
                // NOTE: This block is the block referenced when calculating the elapsed time since the previous update...
                final String blockData = IoUtil.getResource("/blocks/000000000FA8BFA0F0DD32F956B874B2C7F1772C5FBEDCB1B35E03335C7FB0A8");
                final Block block30240 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));

                databaseConnection.executeSql(
                    new Query("UPDATE blocks SET hash = ? WHERE id = ?")
                        .setParameter(block30240.getPreviousBlockHash())
                        .setParameter(blockHeaderDatabaseManager.getHeadBlockHeaderId())
                );

                final BlockId blockId;
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockId = blockDatabaseManager.insertBlock(block30240);
                }
                final Difficulty blockDifficulty = block30240.getDifficulty();
                Assert.assertEquals(Difficulty.BASE_DIFFICULTY, blockDifficulty);
                Assert.assertEquals(2016, blockHeaderDatabaseManager.getBlockHeight(blockId).longValue());
            }

            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                // _storeBlocks(2014, (DateUtil.datetimeToTimestamp("2009-12-18 09:56:01", timeZone) / 1000L) + 1);
                _storeBlocks(1, (DateUtil.datetimeToTimestamp("2009-12-18 09:56:01", timeZone) / 1000L) + 1);
                databaseConnection.executeSql(
                    new Query("UPDATE blocks SET block_height = ? WHERE id = ?")
                        .setParameter(4030)
                        .setParameter(blockHeaderDatabaseManager.getHeadBlockHeaderId())
                );
            }

            // Timestamp: 23EC3A4B
            // Block Height: 4031
            // Block Hash: 00000000984F962134A7291E3693075AE03E521F0EE33378EC30A334D860034B
            { // Store the previous block so the firstBlockWithDifficultyAdjustment has the correct hash...
                final String blockData = IoUtil.getResource("/blocks/00000000984F962134A7291E3693075AE03E521F0EE33378EC30A334D860034B");
                final Block previousBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));

                // previousBlock.setPreviousBlockHash(blockDatabaseManager.getHeadBlockHash()); // Modify this (real) block so that it is on the same chain as the previous (faked) blocks.
                databaseConnection.executeSql(
                    new Query("UPDATE blocks SET hash = ? WHERE id = ?")
                        .setParameter(previousBlock.getPreviousBlockHash())
                        .setParameter(blockHeaderDatabaseManager.getHeadBlockHeaderId())
                );

                final BlockId blockId;
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockId = blockDatabaseManager.insertBlock(previousBlock);
                }
                final Difficulty blockDifficulty = previousBlock.getDifficulty();
                Assert.assertEquals(Difficulty.BASE_DIFFICULTY, blockDifficulty);
                Assert.assertEquals((blockHeight - 1), blockHeaderDatabaseManager.getBlockHeight(blockId).longValue());
            }

            final Difficulty expectedDifficulty = new ImmutableDifficulty(ByteArray.fromHexString("00D86A"), Difficulty.BASE_DIFFICULTY_EXPONENT);
            final float expectedDifficultyRatio = 1.18F;
            // Original Block with the first difficulty adjustment: 000000004F2886A170ADB7204CB0C7A824217DD24D11A74423D564C4E0904967 (Block Height: 32256)
            //  blockData is a modified version of this block, so that its previous block hash fits the modified block above.
            final String blockData = IoUtil.getResource("/blocks/000000004F2886A170ADB7204CB0C7A824217DD24D11A74423D564C4E0904967");

            final Block firstBlockWithDifficultyIncrease;
            {
                final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));

                firstBlockWithDifficultyIncrease = block;
                System.out.println(firstBlockWithDifficultyIncrease.getHash());

                // Hack the hash of the block before this so firstBlockWithDifficultyIncrease can be inserted...
                databaseConnection.executeSql(
                    new Query("UPDATE blocks SET hash = ? WHERE id = ?")
                        .setParameter(block.getPreviousBlockHash())
                        .setParameter(blockHeaderDatabaseManager.getHeadBlockHeaderId())
                );
            }

            final Difficulty blockDifficulty = firstBlockWithDifficultyIncrease.getDifficulty();
            Assert.assertEquals(expectedDifficulty, blockDifficulty);
            Assert.assertEquals(expectedDifficultyRatio, blockDifficulty.getDifficultyRatio().floatValue(), 0.005);

            final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(databaseManager);
            final Difficulty calculatedNextDifficulty = difficultyCalculator.calculateRequiredDifficulty();
            Assert.assertEquals(expectedDifficulty, calculatedNextDifficulty);

            final BlockId blockId;
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockId = blockDatabaseManager.insertBlock(firstBlockWithDifficultyIncrease);
                Assert.assertEquals(blockHeight, blockHeaderDatabaseManager.getBlockHeight(blockId).longValue());
            }

            // Action
            final Boolean blockIsValid = blockValidator.validateBlock(blockId, firstBlockWithDifficultyIncrease).isValid;

            // Assert
            Assert.assertTrue(blockIsValid);
        }
    }

    @Test
    public void should_not_validate_transaction_when_transaction_output_is_only_found_on_separate_fork() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {

            final BlockInflater blockInflater = new BlockInflater();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            final TransactionValidatorFactory transactionValidatorFactory = new TransactionValidatorFactory();
            final BlockValidator blockValidator = new BlockValidator(_readUncomittedDatabaseManagerFactory, transactionValidatorFactory, new ImmutableNetworkTime(Long.MAX_VALUE), new FakeMedianBlockTime());

            final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
            final Block block1 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));
            final Block block2 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_2));
            final Block block3 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_3));
            final Block block4 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_4));
            final Block block5 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_5));

            final Block customBlock6 = blockInflater.fromBytes(HexUtil.hexStringToByteArray("01000000FC33F596F822A0A1951FFDBF2A897B095636AD871707BF5D3162729B00000000E04DAA8565BEFFCEF1949AC5582B7DF359A10A2138409503A1B8B8D3C7355D539CC56649FFFF001D4A0CDDD801010000000100000000000000000000000000000000000000000000000000000000000000000000000020184D696E65642076696120426974636F696E2D56657264652E06313134353332FFFFFFFF0100F2052A010000001976A914F1A626E143DCC5E75E8E6BE3F2CE1CF3108FB53D88AC00000000"));
            final Block invalidBlock6 = blockInflater.fromBytes(HexUtil.hexStringToByteArray("01000000FC33F596F822A0A1951FFDBF2A897B095636AD871707BF5D3162729B00000000CA62264F9C5F8C91919DEEB07AEBAEA6D7699B370027EBA290C2154C6344476EA730975BFFFF001D2070BB0202010000000100000000000000000000000000000000000000000000000000000000000000000000000019184D696E65642076696120426974636F696E2D56657264652EFFFFFFFF0100F2052A010000001976A914F1A626E143DCC5E75E8E6BE3F2CE1CF3108FB53D88AC000000000100000001E04DAA8565BEFFCEF1949AC5582B7DF359A10A2138409503A1B8B8D3C7355D53000000008A47304402202876A32EBDA4BB8D29F5E7596CF0B0F4E9C97D3BDF4C15BE4F13CA64692B002802201F3C9A1B2474907CAE9C505CDD96C8B2F7B7277098FBBA2ED5BAE4BC7C45A4750141046C1D8C923D8ADFCEA711BE28A9BF7E2981632AAC789AEF95D7402B9225784AD93661700AB5474EFFDD7D5BEA6100904D3F1B3BE2017E2A18971DD8904B522020FFFFFFFF0100F2052A010000001976A914F1A626E143DCC5E75E8E6BE3F2CE1CF3108FB53D88AC00000000"));

            // NOTE: invalidBlock6 attempts to spend an output that only exists within customBlock6. Since the TransactionOutput does not exist on its chain, this block is invalid.
            Assert.assertEquals(customBlock6.getTransactions().get(0).getHash(), invalidBlock6.getTransactions().get(1).getTransactionInputs().get(0).getPreviousOutputTransactionHash());

            final BlockId blockId;
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                for (final Block block : new Block[]{genesisBlock, block1, block2, block3, block4, block5}) {
                    blockDatabaseManager.storeBlock(block);
                }

                blockDatabaseManager.storeBlock(customBlock6);
                blockId = blockDatabaseManager.storeBlock(invalidBlock6);
            }

            // Action
            final Boolean block2PrimeIsValid = blockValidator.validateBlock(blockId, invalidBlock6).isValid;

            // Assert
            Assert.assertFalse(block2PrimeIsValid);
        }
    }

    @Test
    public void should_not_validate_block_that_contains_a_duplicate_transaction() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            final BlockInflater blockInflater = new BlockInflater();
            final AddressInflater addressInflater = new AddressInflater();
            final TransactionSigner transactionSigner = new TransactionSigner();
            final TransactionValidatorFactory transactionValidatorFactory = new TransactionValidatorFactory();
            final TransactionValidator transactionValidator = transactionValidatorFactory.newTransactionValidator(databaseManager, new ImmutableNetworkTime(Long.MAX_VALUE), new ImmutableMedianBlockTime(Long.MAX_VALUE));
            final BlockValidator blockValidator = new BlockValidator(_readUncomittedDatabaseManagerFactory, transactionValidatorFactory, new ImmutableNetworkTime(Long.MAX_VALUE), new FakeMedianBlockTime());

            final TransactionOutputRepository transactionOutputRepository = new DatabaseTransactionOutputRepository(databaseManager);

            Block lastBlock = null;
            BlockId lastBlockId = null;
            for (final String blockData : new String[] { BlockData.MainChain.GENESIS_BLOCK, BlockData.MainChain.BLOCK_1, BlockData.MainChain.BLOCK_2 }) {
                final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    lastBlockId = blockDatabaseManager.storeBlock(block);
                }
                lastBlock = block;
            }
            Assert.assertNotNull(lastBlock);
            Assert.assertNotNull(lastBlockId);

            final PrivateKey privateKey = PrivateKey.createNewKey();

            final Transaction transactionToSpend;
            final MutableBlock mutableBlock = new MutableBlock() {
                @Override
                public Sha256Hash getHash() {
                    return Sha256Hash.fromHexString("0000000082B5015589A3FDF2D4BAFF403E6F0BE035A5D9742C1CAE6295464449"); // Block 3's hash...
                }

                @Override
                public Boolean isValid() {
                    return true;
                }
            };

            {
                mutableBlock.setDifficulty(lastBlock.getDifficulty());
                mutableBlock.setNonce(lastBlock.getNonce());
                mutableBlock.setTimestamp(lastBlock.getTimestamp());
                mutableBlock.setPreviousBlockHash(lastBlock.getHash());
                mutableBlock.setVersion(lastBlock.getVersion());

                // Create a transaction that will be spent in our signed transaction.
                //  This transaction will create an output that can be spent by our private key.
                transactionToSpend = TransactionValidatorTests._createTransactionContaining(
                    TransactionValidatorTests._createCoinbaseTransactionInput(),
                    TransactionValidatorTests._createTransactionOutput(addressInflater.fromPrivateKey(privateKey), 50L * Transaction.SATOSHIS_PER_BITCOIN)
                );

                mutableBlock.addTransaction(transactionToSpend);

                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockDatabaseManager.storeBlock(mutableBlock);
                }
            }

            final Transaction signedTransaction;
            {
                final MutableTransaction unsignedTransaction = TransactionValidatorTests._createTransactionContaining(
                    TransactionValidatorTests._createTransactionInputThatSpendsTransaction(transactionToSpend),
                    TransactionValidatorTests._createTransactionOutput(addressInflater.fromBase58Check("1HrXm9WZF7LBm3HCwCBgVS3siDbk5DYCuW"), 1L * Transaction.SATOSHIS_PER_BITCOIN)
                );

                // Sign the transaction..
                final SignatureContextGenerator signatureContextGenerator = new SignatureContextGenerator(transactionOutputRepository);
                final SignatureContext signatureContext = signatureContextGenerator.createContextForEntireTransaction(unsignedTransaction, false);
                signedTransaction = transactionSigner.signTransaction(signatureContext, privateKey);

                transactionDatabaseManager.storeTransaction(signedTransaction);
            }

            { // Ensure the fake transaction that will be duplicated would normally be valid on its own...
                final Boolean isValid = transactionValidator.validateTransaction(BlockchainSegmentId.wrap(1L), TransactionValidatorTests.calculateBlockHeight(databaseManager), signedTransaction, false);
                Assert.assertTrue(isValid);
            }

            mutableBlock.addTransaction(signedTransaction);
            mutableBlock.addTransaction(signedTransaction); // Add the valid transaction twice...

            final BlockId blockId;
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                try {
                    blockId = blockDatabaseManager.storeBlock(mutableBlock);
                }
                catch (final DatabaseException exception) {
                    return; // Failing to insert the duplicate transaction is sufficient to pass the test...
                }
            }

            // Action
            final Boolean blockIsValid = blockValidator.validateBlock(blockId, mutableBlock).isValid;

            // Assert
            Assert.assertFalse(blockIsValid);
        }
    }

    // @Test
    public void should_be_allowed_to_spend_transactions_with_duplicate_identifiers_in_the_same_block() throws Exception {
         /* Excerpt from the current uncertainty surrounding whether or not this is valid.

                ... According to BIP 30, duplicate transactions are only allowed if they've been previously spent
                (except the two grandfathered instances).  ("Blocks are not allowed to contain a transaction whose
                identifier matches that of an earlier, not-fully-spent transaction in the same chain.")

                ...the person controlling the keys for the grandfathered transactions can produce a complicated scenario
                that bends these rules (assuming they're even allowed to spend them...

                Assume the grandfathered duplicate-txid coinbases are "A1" and "A2".  Also let "B1" be a new tx that
                spends one of the A1/A2 outputs (and where "B2" is essentially a duplicate of "B1", spending the prevout
                to the same address as B1 (so B1.txid == B2.txid)).  BIP30 prevents B2 from being accepted unless B1 has
                already been spent.  So once B1 is spent (by "C1"), B2 becomes valid since B1 is now "spent". Normally,
                this isn't a practical use-case since the mempool rejects duplicate txids, but if these transactions
                were broadcast as a block (with ordering being [<Coinbase>, B1, C1, B2]), would it be considered a valid
                block? If so, what happens if/when CTOR is implemented?

            Until clear consensus is decided to handle this situation, Bitcoin-Verde considers duplicate transactions in the
            same block an invalid block.

         */
    }

    @Test
    public void should_not_be_allowed_to_spend_transactions_with_duplicate_identifiers_more_than_the_number_of_times_they_are_duplicated() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            final BlockInflater blockInflater = new BlockInflater();
            final AddressInflater addressInflater = new AddressInflater();
            final TransactionSigner transactionSigner = new TransactionSigner();
            final TransactionValidatorFactory transactionValidatorFactory = new TransactionValidatorFactory();
            final TransactionValidator transactionValidator = transactionValidatorFactory.newTransactionValidator(databaseManager, new ImmutableNetworkTime(Long.MAX_VALUE), new ImmutableMedianBlockTime(Long.MAX_VALUE));
            final BlockValidator blockValidator = new BlockValidator(_readUncomittedDatabaseManagerFactory, transactionValidatorFactory, new ImmutableNetworkTime(Long.MAX_VALUE), new FakeMedianBlockTime());
            final TransactionOutputRepository transactionOutputRepository = new DatabaseTransactionOutputRepository(databaseManager);

            Sha256Hash lastBlockHash = null;
            Block lastBlock = null;
            BlockId lastBlockId = null;
            for (final String blockData : new String[] { BlockData.MainChain.GENESIS_BLOCK, BlockData.MainChain.BLOCK_1, BlockData.MainChain.BLOCK_2 }) {
                final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    lastBlockId = blockDatabaseManager.storeBlock(block);
                }
                lastBlock = block;
                lastBlockHash = block.getHash();
            }
            Assert.assertNotNull(lastBlock);
            Assert.assertNotNull(lastBlockId);
            Assert.assertNotNull(lastBlockHash);

            final PrivateKey privateKey = PrivateKey.createNewKey();

            final Transaction validCoinbaseWithDuplicateIdentifier;
            final MutableBlock blockWithDuplicateTxId = new MutableBlock() {
                @Override
                public Boolean isValid() { return true; } // Disables basic header validation...
            };

            {
                blockWithDuplicateTxId.setDifficulty(lastBlock.getDifficulty());
                blockWithDuplicateTxId.setNonce(lastBlock.getNonce());
                blockWithDuplicateTxId.setTimestamp(lastBlock.getTimestamp());
                blockWithDuplicateTxId.setVersion(lastBlock.getVersion());

                // Create a transaction that will be spent in our signed transaction.
                //  This transaction will create an output that can be spent by our private key.
                validCoinbaseWithDuplicateIdentifier = TransactionValidatorTests._createTransactionContaining(
                    TransactionValidatorTests._createCoinbaseTransactionInput(),
                    TransactionValidatorTests._createTransactionOutput(addressInflater.fromPrivateKey(privateKey), 50L * Transaction.SATOSHIS_PER_BITCOIN)
                );

                blockWithDuplicateTxId.addTransaction(validCoinbaseWithDuplicateIdentifier);

                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockWithDuplicateTxId.setPreviousBlockHash(lastBlockHash);
                    final BlockId blockId = blockDatabaseManager.storeBlock(blockWithDuplicateTxId); // Block3
                    lastBlockHash = blockWithDuplicateTxId.getHash();

                    final Boolean blockIsValid = blockValidator.validateBlock(blockId, blockWithDuplicateTxId).isValid;
                    Assert.assertTrue(blockIsValid);
                }
            }

            final Transaction signedTransactionSpendingDuplicateCoinbase;
            {
                final MutableTransaction unsignedTransaction = TransactionValidatorTests._createTransactionContaining(
                    TransactionValidatorTests._createTransactionInputThatSpendsTransaction(validCoinbaseWithDuplicateIdentifier),
                    TransactionValidatorTests._createTransactionOutput(addressInflater.fromBase58Check("1HrXm9WZF7LBm3HCwCBgVS3siDbk5DYCuW"), 50L * Transaction.SATOSHIS_PER_BITCOIN)
                );

                // Sign the transaction..
                final SignatureContextGenerator signatureContextGenerator = new SignatureContextGenerator(transactionOutputRepository);
                final SignatureContext signatureContext = signatureContextGenerator.createContextForEntireTransaction(unsignedTransaction, false);
                signedTransactionSpendingDuplicateCoinbase = transactionSigner.signTransaction(signatureContext, privateKey);

                transactionDatabaseManager.storeTransaction(signedTransactionSpendingDuplicateCoinbase);
            }

            { // Ensure the fake transaction that will be duplicated would normally be valid on its own...
                final Boolean isValid = transactionValidator.validateTransaction(BlockchainSegmentId.wrap(1L), TransactionValidatorTests.calculateBlockHeight(databaseManager), signedTransactionSpendingDuplicateCoinbase, false);
                Assert.assertTrue(isValid);
            }

            { // Spend the soon-to-be-duplicated coinbase...
                final MutableBlock mutableBlock = new MutableBlock(blockWithDuplicateTxId) {
                    @Override
                    public Boolean isValid() { return true; }
                };
                mutableBlock.clearTransactions();

                final Transaction regularCoinbaseTransaction = TransactionValidatorTests._createTransactionContaining(
                    TransactionValidatorTests._createCoinbaseTransactionInput(),
                    TransactionValidatorTests._createTransactionOutput(addressInflater.fromBase58Check("13usM2ns3f466LP65EY1h8hnTBLFiJV6rD"), 50L * Transaction.SATOSHIS_PER_BITCOIN)
                );

                mutableBlock.addTransaction(regularCoinbaseTransaction);
                mutableBlock.addTransaction(signedTransactionSpendingDuplicateCoinbase);

                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    mutableBlock.setPreviousBlockHash(lastBlockHash);
                    final BlockId blockId = blockDatabaseManager.storeBlock(mutableBlock); // Block4
                    lastBlockHash = mutableBlock.getHash();

                    final Boolean blockIsValid = blockValidator.validateBlock(blockId, mutableBlock).isValid;
                    Assert.assertTrue(blockIsValid);
                }
            }

            { // Create a valid duplicate TxId...
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockWithDuplicateTxId.setPreviousBlockHash(lastBlockHash);
                    final BlockId blockId = blockDatabaseManager.storeBlock(blockWithDuplicateTxId); // Block5
                    lastBlockHash = blockWithDuplicateTxId.getHash();

                    final Boolean blockIsValid = blockValidator.validateBlock(blockId, blockWithDuplicateTxId).isValid;
                    Assert.assertTrue(blockIsValid);
                }
            }

            // NOTE: At this point, block3 and block5 are "valid" blocks, whose coinbases share the same identifier.
            //  According to the protocol, this is technically valid (see BIP-30: https://github.com/bitcoin/bips/blob/master/bip-0030.mediawiki) since the output has been spent in block4.
            //  While a duplicate transaction has never actually been spent on the main net, and it is unlikely to ever happen, it is important to handle the scenario correctly.

            { // Spend the duplicate tx-id a second time (should be valid)...
                final MutableBlock mutableBlock = new MutableBlock(blockWithDuplicateTxId) {
                    @Override
                    public Boolean isValid() { return true; }
                };
                mutableBlock.clearTransactions();

                final Transaction regularCoinbaseTransaction = TransactionValidatorTests._createTransactionContaining(
                    TransactionValidatorTests._createCoinbaseTransactionInput(),
                    TransactionValidatorTests._createTransactionOutput(addressInflater.fromBase58Check("1N7ABymxVuekZ3B37xkU2u2XPygDg1bwZR"), 50L * Transaction.SATOSHIS_PER_BITCOIN)
                );

                mutableBlock.addTransaction(regularCoinbaseTransaction);
                mutableBlock.addTransaction(signedTransactionSpendingDuplicateCoinbase);

                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    mutableBlock.setPreviousBlockHash(lastBlockHash);
                    final BlockId blockId = blockDatabaseManager.storeBlock(mutableBlock); // Block6
                    lastBlockHash = mutableBlock.getHash();

                    final Boolean blockIsValid = blockValidator.validateBlock(blockId, mutableBlock).isValid;
                    Assert.assertTrue(blockIsValid);
                }
            }

            { // Spend the duplicate tx-id a third time (should be invalid)...
                final MutableBlock mutableBlock = new MutableBlock(blockWithDuplicateTxId) {
                    @Override
                    public Boolean isValid() { return true; }
                };
                mutableBlock.clearTransactions();

                final Transaction regularCoinbaseTransaction = TransactionValidatorTests._createTransactionContaining(
                    TransactionValidatorTests._createCoinbaseTransactionInput(),
                    TransactionValidatorTests._createTransactionOutput(addressInflater.fromBase58Check("18rComAH12mPMG53hyvWB6ewAN26TXK6rU"), 50L * Transaction.SATOSHIS_PER_BITCOIN)
                );

                mutableBlock.addTransaction(regularCoinbaseTransaction);
                mutableBlock.addTransaction(signedTransactionSpendingDuplicateCoinbase);

                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    mutableBlock.setPreviousBlockHash(lastBlockHash);
                    final BlockId blockId = blockDatabaseManager.storeBlock(mutableBlock); // Block7
                    lastBlockHash = mutableBlock.getHash();

                    final Boolean blockIsValid = blockValidator.validateBlock(blockId, mutableBlock).isValid;
                    Assert.assertFalse(blockIsValid);
                }
            }
        }
    }

    @Test
    public void should_not_be_invalid_if_spent_on_different_chain() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            final BlockInflater blockInflater = new BlockInflater();
            final AddressInflater addressInflater = new AddressInflater();
            final TransactionSigner transactionSigner = new TransactionSigner();
            final TransactionValidatorFactory transactionValidatorFactory = new TransactionValidatorFactory();
            final TransactionValidator transactionValidator = transactionValidatorFactory.newTransactionValidator(databaseManager, new ImmutableNetworkTime(Long.MAX_VALUE), new ImmutableMedianBlockTime(Long.MAX_VALUE));
            final BlockValidator blockValidator = new BlockValidator(_readUncomittedDatabaseManagerFactory, transactionValidatorFactory, new ImmutableNetworkTime(Long.MAX_VALUE), new FakeMedianBlockTime());
            final TransactionOutputRepository transactionOutputRepository = new DatabaseTransactionOutputRepository(databaseManager);

            Sha256Hash lastBlockHash = null;
            Block lastBlock = null;
            BlockId lastBlockId = null;
            for (final String blockData : new String[] { BlockData.MainChain.GENESIS_BLOCK, BlockData.MainChain.BLOCK_1, BlockData.MainChain.BLOCK_2 }) {
                final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    lastBlockId = blockDatabaseManager.storeBlock(block);
                }
                lastBlock = block;
                lastBlockHash = block.getHash();
            }
            Assert.assertNotNull(lastBlock);
            Assert.assertNotNull(lastBlockId);
            Assert.assertNotNull(lastBlockHash);

            final PrivateKey privateKey = PrivateKey.createNewKey();

            final Transaction spendableCoinbase;
            final MutableBlock blockWithSpendableCoinbase = new MutableBlock() {
                @Override
                public Boolean isValid() { return true; } // Disables basic header validation...
            };

            {
                blockWithSpendableCoinbase.setDifficulty(lastBlock.getDifficulty());
                blockWithSpendableCoinbase.setNonce(lastBlock.getNonce());
                blockWithSpendableCoinbase.setTimestamp(lastBlock.getTimestamp());
                blockWithSpendableCoinbase.setVersion(lastBlock.getVersion());

                // Create a transaction that will be spent in our signed transaction.
                //  This transaction will create an output that can be spent by our private key.
                spendableCoinbase = TransactionValidatorTests._createTransactionContaining(
                    TransactionValidatorTests._createCoinbaseTransactionInput(),
                    TransactionValidatorTests._createTransactionOutput(addressInflater.fromPrivateKey(privateKey), 50L * Transaction.SATOSHIS_PER_BITCOIN)
                );

                blockWithSpendableCoinbase.addTransaction(spendableCoinbase);

                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockWithSpendableCoinbase.setPreviousBlockHash(lastBlockHash);
                    final BlockId blockId = blockDatabaseManager.storeBlock(blockWithSpendableCoinbase); // Block3
                    lastBlockHash = blockWithSpendableCoinbase.getHash();

                    final Boolean blockIsValid = blockValidator.validateBlock(blockId, blockWithSpendableCoinbase).isValid;
                    Assert.assertTrue(blockIsValid);
                }
            }

            final Transaction signedTransactionSpendingDuplicateCoinbase;
            {
                final MutableTransaction unsignedTransaction = TransactionValidatorTests._createTransactionContaining(
                        TransactionValidatorTests._createTransactionInputThatSpendsTransaction(spendableCoinbase),
                        TransactionValidatorTests._createTransactionOutput(addressInflater.fromBase58Check("1HrXm9WZF7LBm3HCwCBgVS3siDbk5DYCuW"), 50L * Transaction.SATOSHIS_PER_BITCOIN)
                );

                // Sign the transaction..
                final SignatureContextGenerator signatureContextGenerator = new SignatureContextGenerator(transactionOutputRepository);
                final SignatureContext signatureContext = signatureContextGenerator.createContextForEntireTransaction(unsignedTransaction, false);
                signedTransactionSpendingDuplicateCoinbase = transactionSigner.signTransaction(signatureContext, privateKey);

                transactionDatabaseManager.storeTransaction(signedTransactionSpendingDuplicateCoinbase);
            }

            { // Ensure the transaction would normally be valid on its own...
                final Boolean isValid = transactionValidator.validateTransaction(BlockchainSegmentId.wrap(1L), TransactionValidatorTests.calculateBlockHeight(databaseManager), signedTransactionSpendingDuplicateCoinbase, false);
                Assert.assertTrue(isValid);
            }

            { // Spend the coinbase...
                final MutableBlock mutableBlock = new MutableBlock(blockWithSpendableCoinbase) {
                    @Override
                    public Boolean isValid() { return true; }
                };
                mutableBlock.clearTransactions();

                final Transaction regularCoinbaseTransaction = TransactionValidatorTests._createTransactionContaining(
                        TransactionValidatorTests._createCoinbaseTransactionInput(),
                        TransactionValidatorTests._createTransactionOutput(addressInflater.fromBase58Check("13usM2ns3f466LP65EY1h8hnTBLFiJV6rD"), 50L * Transaction.SATOSHIS_PER_BITCOIN)
                );

                mutableBlock.addTransaction(regularCoinbaseTransaction);
                mutableBlock.addTransaction(signedTransactionSpendingDuplicateCoinbase);

                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    mutableBlock.setPreviousBlockHash(lastBlockHash);
                    final BlockId blockId = blockDatabaseManager.storeBlock(mutableBlock); // Block4
                    lastBlockHash = mutableBlock.getHash();

                    final Boolean blockIsValid = blockValidator.validateBlock(blockId, mutableBlock).isValid;
                    Assert.assertTrue(blockIsValid);
                }
            }

            { // Spend the coinbase on a separate chain...
                final MutableBlock mutableBlock = new MutableBlock(blockWithSpendableCoinbase) {
                    @Override
                    public Boolean isValid() { return true; }
                };
                mutableBlock.clearTransactions();

                final Transaction regularCoinbaseTransaction = TransactionValidatorTests._createTransactionContaining(
                    TransactionValidatorTests._createCoinbaseTransactionInput(),
                    TransactionValidatorTests._createTransactionOutput(addressInflater.fromBase58Check("1DgiazmkoTEdvTa6ErdzrqvmnenGS11RU2"), 50L * Transaction.SATOSHIS_PER_BITCOIN)
                );

                mutableBlock.addTransaction(regularCoinbaseTransaction);
                mutableBlock.addTransaction(signedTransactionSpendingDuplicateCoinbase);

                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    mutableBlock.setPreviousBlockHash(blockWithSpendableCoinbase.getHash());
                    final BlockId blockId = blockDatabaseManager.storeBlock(mutableBlock); // Block4Prime
                    lastBlockHash = mutableBlock.getHash();

                    final Boolean blockIsValid = blockValidator.validateBlock(blockId, mutableBlock).isValid;
                    Assert.assertTrue(blockIsValid);
                }
            }
        }
    }

    @Test
    public void block_validator_should_close_all_database_connections_when_multithreaded_validation_is_executed() throws Exception {
        // This test stores a "big block" (1,000+ Txns) and then validates it multiple times with a DatabaseConnectionPool, a DatabaseCache, and a varying number of validation threads.
        // The Block should be validated correctly and all of its transactions should closed after closing the pool (no DatabaseConnections were leaked).
        // The DatabaseConnectionPool is configured to provide less DatabaseConnections (16) than is required for the BlockValidator with 32 threads.  This tests that the Validator
        //  properly waits for a connection to become available.  Alternatively, the DeadlockTimeout property of the DatabaseConnectionPool can be configured to surpass the maximum connection count.

        // Setup
        try (
                final MasterDatabaseManagerCache masterDatabaseManagerCache = new MasterDatabaseManagerCacheCore();
                final LocalDatabaseManagerCache databaseManagerCache = new LocalDatabaseManagerCache(masterDatabaseManagerCache);
                final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()
        ) {
            final BlockInflater blockInflater = new BlockInflater();
            final DatabaseConnection databaseConnection = _database.newConnection();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            final DatabaseConnectionFactory coreDatabaseConnectionFactory = _database.getDatabaseConnectionFactory();
            final DatabaseConnectionFactory databaseConnectionFactory = new DatabaseConnectionFactory() {
                @Override
                public DatabaseConnection newConnection() throws DatabaseException {
                    return new MysqlDatabaseConnectionWrapper(new MonitoredDatabaseConnection(coreDatabaseConnectionFactory.newConnection().getRawConnection()));
                }

                @Override
                public void close() throws DatabaseException {
                    coreDatabaseConnectionFactory.close();
                }
            };
            // final DatabaseConnectionPool databaseConnectionPool = new DatabaseConnectionPool(databaseConnectionFactory, 16, 30000L);
            final ReadUncommittedDatabaseConnectionFactory readUncommittedDatabaseConnectionFactory = new ReadUncommittedDatabaseConnectionFactoryWrapper(databaseConnectionFactory);
            final FullNodeDatabaseManagerFactory databaseManagerFactory = new FullNodeDatabaseManagerFactory(readUncommittedDatabaseConnectionFactory, databaseManagerCache);

            final TransactionValidatorFactory transactionValidatorFactory = new TransactionValidatorFactory();
            final BlockValidator blockValidator = new BlockValidator(databaseManagerFactory, transactionValidatorFactory, new ImmutableNetworkTime(Long.MAX_VALUE), new FakeMedianBlockTime());
            blockValidator.setMaxThreadCount(8);

            final String bigBlockPreRequisite = IoUtil.getResource("/blocks/0000000000000000013C4F15DDF9040B6210DC86DFBE7371417EF83EA7BFBA34");
            final String bigBlock = IoUtil.getResource("/blocks/00000000000000000051CFB8C9B8191EC4EF14F8F44F3E2290D67A8A0A29DD05");

            Block lastBlock = null;
            BlockId lastBlockId = null;
            int i = 0;
            for (final String blockData : new String[] { BlockData.MainChain.GENESIS_BLOCK, BlockData.MainChain.BLOCK_1, BlockData.MainChain.BLOCK_2, bigBlockPreRequisite, bigBlock }) {
                final MutableBlock block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    if (i == 3) {
                        databaseConnection.executeSql(new Query("UPDATE blocks SET hash = ? WHERE id = ?").setParameter(block.getPreviousBlockHash()).setParameter(lastBlockId));

                        lastBlockId = blockHeaderDatabaseManager.storeBlockHeader(block);
                        lastBlock = block;
                        i += 1;
                        continue;
                    }
                    else if (i == 4) {
                        boolean isCoinbase = true;
                        for (final Transaction transaction : block.getTransactions()) {
                            if (! isCoinbase) {
                                TransactionTestUtil.createRequiredTransactionInputs(databaseManager, BlockchainSegmentId.wrap(1L), transaction);
                            }
                            isCoinbase = false;
                        }
                    }
                    lastBlockId = blockDatabaseManager.storeBlock(block);
                    lastBlock = block;
                    i += 1;
                }
            }

            for (int j = 0; j < 6; ++j) {
                final int threadCount = (int) Math.pow(2, j);
                Logger.info("");
                Logger.info("Validating Block w/ " + threadCount + " threads.");
                blockValidator.setMaxThreadCount(threadCount);
                blockValidator.validateBlock(lastBlockId, lastBlock);

                Assert.assertEquals(0, MonitoredDatabaseConnection.databaseConnectionCount.get());
            }

            // Action
            final BlockValidationResult blockValidationResult = blockValidator.validateBlock(lastBlockId, lastBlock);
            final Boolean blockIsValid = blockValidationResult.isValid;

            // Assert
            Assert.assertTrue(blockIsValid);

            readUncommittedDatabaseConnectionFactory.close();
            Assert.assertEquals(0, MonitoredDatabaseConnection.databaseConnectionCount.get());
        }
    }
}

class MonitoredDatabaseConnection extends MysqlDatabaseConnection {
    public static final AtomicInteger databaseConnectionCount = new AtomicInteger(0);

    public MonitoredDatabaseConnection(final Connection rawConnection) {
        super(rawConnection);
        databaseConnectionCount.incrementAndGet();
    }

    @Override
    public void close() throws DatabaseException {
        databaseConnectionCount.decrementAndGet();
        super.close();
    }
}
