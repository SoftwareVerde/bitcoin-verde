package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.ImmutableDifficulty;
import com.softwareverde.bitcoin.chain.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.network.MutableNetworkTime;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.coinbase.CoinbaseTransaction;
import com.softwareverde.bitcoin.transaction.coinbase.MutableCoinbaseTransaction;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.OperationInflater;
import com.softwareverde.bitcoin.transaction.script.unlocking.MutableUnlockingScript;
import com.softwareverde.bitcoin.type.address.AddressInflater;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.type.key.PrivateKey;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.factory.ReadUncommittedDatabaseConnectionFactory;
import com.softwareverde.util.DateUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.IoUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.TimeZone;

public class BlockValidatorTests extends IntegrationTest {
    final PrivateKey _privateKey = PrivateKey.parseFromHexString("2F9DFE0F574973D008DA9A98D1D39422D044154E2008E195643AD026F1B2B554");

    private class FakeNetworkTime extends MutableNetworkTime {
        private final Long _fakeTime;

        public FakeNetworkTime(final Long fakeTime) {
            _fakeTime = fakeTime;
        }

        @Override
        public Long getCurrentTimeInSeconds() {
            return _fakeTime;
        }

        @Override
        public Long getCurrentTimeInMilliSeconds() {
            return _fakeTime;
        }
    }

    private void _storeBlocks(final int blockCount, final Long timestamp) throws Exception {
        try (final MysqlDatabaseConnection databaseConnection = _database.newConnection()) {
            final BlockInflater blockInflater = new BlockInflater();
            final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

            for (int i=0; i<blockCount; ++i) {
                final Sha256Hash mostRecentBlockHash = blockDatabaseManager.getHeadBlockHash();
                MutableBlock block;

                if (mostRecentBlockHash == null) {
                    final String genesisBlockData = IoUtil.getResource("/blocks/" + HexUtil.toHexString(BlockHeader.GENESIS_BLOCK_HEADER_HASH.getBytes()));
                    block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(genesisBlockData));
                }
                else {
                    final Long blockHeight = null;
                    final BlockId blockId = blockDatabaseManager.getBlockIdFromHash(mostRecentBlockHash);
                    final BlockHeader blockHeader = blockDatabaseManager.getBlockHeader(blockId);
                    final ImmutableListBuilder<Transaction> listBuilder = new ImmutableListBuilder<Transaction>(1);
                    final AddressInflater addressInflater = new AddressInflater();
                    listBuilder.add(Transaction.createCoinbaseTransaction(blockHeight, "Fake Block", addressInflater.fromPrivateKey(_privateKey), 50 * Transaction.SATOSHIS_PER_BITCOIN));
                    block = new MutableBlock(blockHeader, listBuilder.build());

                    block.setPreviousBlockHash(mostRecentBlockHash);
                    block.setNonce(block.getNonce() + 1);
                    block.setTimestamp(timestamp + i);
                }

                blockDatabaseManager.storeBlock(block);
                blockChainDatabaseManager.updateBlockChainsForNewBlock(block);
            }
        }
    }

    @Before
    public void setup() throws Exception {
        _resetDatabase();
    }

    @Test
    public void should_validate_block_that_contains_transaction_that_spends_its_own_outputs() throws Exception {
        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockInflater blockInflater = new BlockInflater();
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final ReadUncommittedDatabaseConnectionFactory connectionFactory = new ReadUncommittedDatabaseConnectionFactory(_database.getDatabaseConnectionFactory());
        final BlockValidator blockValidator = new BlockValidator(connectionFactory, new FakeNetworkTime(Long.MAX_VALUE));

        { // Store the blocks and transactions included within the block-under-test so that it should appear valid...
            // Block Hash: 000000002D947997DC957CDF075DD32390F5F754D2656208D5DD82A6620179F5
            final Block previousPrerequisiteBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray("010000009E36757D22BD738DCBA6F6FC47215839FE149B8B849049DBF305B90900000000E398331A75C87C42E14D571DFA7EF036CF4C06F85D05FEE2D366BB2ACC1B1FD4A5C96E49FFFF001D02B465C10101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0804FFFF001D027705FFFFFFFF0100F2052A01000000434104BA8220A0CDE503EE7F923AB223B07C22E705FB5215E1A3F5E6DFB37CF8A714D0D6D9F2A7A00A61675CF20ABA71233973D1DEA913C130F0AF380ED4A9C2116045AC00000000"));
            blockDatabaseManager.storeBlock(previousPrerequisiteBlock); // This block must be stored so that the prerequisiteBlock will have the correct hash (without this block, prerequisiteBlock's previous_block value is zeroed).

            // Block Hash: 00000000689051C09FF2CD091CC4C22C10B965EB8DB3AD5F032621CC36626175
            final Block prerequisiteBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray("01000000F5790162A682DDD5086265D254F7F59023D35D07DF7C95DC9779942D00000000193028D8B78007269D52B2A1068E32EDD21D0772C2C157954F7174761B78A51A30CE6E49FFFF001D3A2E34480201000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0804FFFF001D027C05FFFFFFFF0100F2052A01000000434104B43BB206B71F34E2FAB9359B156FF683BED889021A06C315722A7C936B9743AD88A8882DC13EECAFCDAD4F082D2D0CC54AA177204F79DC7305F1F4857B7B8802AC00000000010000000177B5E6E78F8552129D07A73801B1A5F6830EC040D218755B46340B4CF6D21FD7000000004A49304602210083EC8BD391269F00F3D714A54F4DBD6B8004B3E9C91F3078FF4FCA42DA456F4D0221008DFE1450870A717F59A494B77B36B7884381233555F8439DAC4EA969977DD3F401FFFFFFFF0200E1F505000000004341044A656F065871A353F216CA26CEF8DDE2F03E8C16202D2E8AD769F02032CB86A5EB5E56842E92E19141D60A01928F8DD2C875A390F67C1F6C94CFC617C0EA45AFAC00180D8F00000000434104F36C67039006EC4ED2C885D7AB0763FEB5DEB9633CF63841474712E4CF0459356750185FC9D962D0F4A1E08E1A84F0C9A9F826AD067675403C19D752530492DCAC00000000"));
            final BlockId prerequisiteBlockId = blockDatabaseManager.storeBlock(prerequisiteBlock);
            blockChainDatabaseManager.updateBlockChainsForNewBlock(prerequisiteBlock);
            final BlockChainSegmentId blockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(prerequisiteBlockId);
            final Boolean prerequisiteBlockIsValid = blockValidator.validateBlock(blockChainSegmentId, prerequisiteBlock);
            Assert.assertFalse(prerequisiteBlockIsValid); // Should fail because its dependent transactions are not inserted.  Asserting for clarity and sanity.
        }

        // Block Hash: 000000005A4DED781E667E06CEEFAFB71410B511FE0D5ADC3E5A27ECBEC34AE6
        final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray("0100000075616236CC2126035FADB38DEB65B9102CC2C41C09CDF29FC051906800000000FE7D5E12EF0FF901F6050211249919B1C0653771832B3A80C66CEA42847F0AE1D4D26E49FFFF001D00F0A4410401000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0804FFFF001D029105FFFFFFFF0100F2052A010000004341046D8709A041D34357697DFCB30A9D05900A6294078012BF3BB09C6F9B525F1D16D5503D7905DB1ADA9501446EA00728668FC5719AA80BE2FDFC8A858A4DBDD4FBAC00000000010000000255605DC6F5C3DC148B6DA58442B0B2CD422BE385EAB2EBEA4119EE9C268D28350000000049483045022100AA46504BAA86DF8A33B1192B1B9367B4D729DC41E389F2C04F3E5C7F0559AAE702205E82253A54BF5C4F65B7428551554B2045167D6D206DFE6A2E198127D3F7DF1501FFFFFFFF55605DC6F5C3DC148B6DA58442B0B2CD422BE385EAB2EBEA4119EE9C268D2835010000004847304402202329484C35FA9D6BB32A55A70C0982F606CE0E3634B69006138683BCD12CBB6602200C28FEB1E2555C3210F1DDDB299738B4FF8BBE9667B68CB8764B5AC17B7ADF0001FFFFFFFF0200E1F505000000004341046A0765B5865641CE08DD39690AADE26DFBF5511430CA428A3089261361CEF170E3929A68AEE3D8D4848B0C5111B0A37B82B86AD559FD2A745B44D8E8D9DFDC0CAC00180D8F000000004341044A656F065871A353F216CA26CEF8DDE2F03E8C16202D2E8AD769F02032CB86A5EB5E56842E92E19141D60A01928F8DD2C875A390F67C1F6C94CFC617C0EA45AFAC0000000001000000025F9A06D3ACDCEB56BE1BFEAA3E8A25E62D182FA24FEFE899D1C17F1DAD4C2028000000004847304402205D6058484157235B06028C30736C15613A28BDB768EE628094CA8B0030D4D6EB0220328789C9A2EC27DDAEC0AD5EF58EFDED42E6EA17C2E1CE838F3D6913F5E95DB601FFFFFFFF5F9A06D3ACDCEB56BE1BFEAA3E8A25E62D182FA24FEFE899D1C17F1DAD4C2028010000004A493046022100C45AF050D3CEA806CEDD0AB22520C53EBE63B987B8954146CDCA42487B84BDD6022100B9B027716A6B59E640DA50A864D6DD8A0EF24C76CE62391FA3EABAF4D2886D2D01FFFFFFFF0200E1F505000000004341046A0765B5865641CE08DD39690AADE26DFBF5511430CA428A3089261361CEF170E3929A68AEE3D8D4848B0C5111B0A37B82B86AD559FD2A745B44D8E8D9DFDC0CAC00180D8F000000004341046A0765B5865641CE08DD39690AADE26DFBF5511430CA428A3089261361CEF170E3929A68AEE3D8D4848B0C5111B0A37B82B86AD559FD2A745B44D8E8D9DFDC0CAC000000000100000002E2274E5FEA1BF29D963914BD301AA63B64DAAF8A3E88F119B5046CA5738A0F6B0000000048473044022016E7A727A061EA2254A6C358376AAA617AC537EB836C77D646EBDA4C748AAC8B0220192CE28BF9F2C06A6467E6531E27648D2B3E2E2BAE85159C9242939840295BA501FFFFFFFFE2274E5FEA1BF29D963914BD301AA63B64DAAF8A3E88F119B5046CA5738A0F6B010000004A493046022100B7A1A755588D4190118936E15CD217D133B0E4A53C3C15924010D5648D8925C9022100AAEF031874DB2114F2D869AC2DE4AE53908FBFEA5B2B1862E181626BB9005C9F01FFFFFFFF0200E1F505000000004341044A656F065871A353F216CA26CEF8DDE2F03E8C16202D2E8AD769F02032CB86A5EB5E56842E92E19141D60A01928F8DD2C875A390F67C1F6C94CFC617C0EA45AFAC00180D8F000000004341046A0765B5865641CE08DD39690AADE26DFBF5511430CA428A3089261361CEF170E3929A68AEE3D8D4848B0C5111B0A37B82B86AD559FD2A745B44D8E8D9DFDC0CAC00000000"));
        final BlockId blockId = blockDatabaseManager.storeBlock(block);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block);
        final BlockChainSegmentId blockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(blockId);

        // Action
        final Boolean blockIsValid = blockValidator.validateBlock(blockChainSegmentId, block);

        // Assert
        Assert.assertTrue(blockIsValid);
    }

    @Test
    public void should_validate_transactions_that_spend_its_coinbase() throws Exception {
        // NOTE: Spending the coinbase transaction within 100 blocks of its mining is invalid.

        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockInflater blockInflater = new BlockInflater();
        final ReadUncommittedDatabaseConnectionFactory connectionFactory = new ReadUncommittedDatabaseConnectionFactory(_database.getDatabaseConnectionFactory());
        final BlockValidator blockValidator = new BlockValidator(connectionFactory, new FakeNetworkTime(Long.MAX_VALUE));
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

        final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final BlockId genesisBlockId = blockDatabaseManager.storeBlock(genesisBlock);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain4.BLOCK_1));
        final BlockId blockId = blockDatabaseManager.storeBlock(block);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block);
        final BlockChainSegmentId blockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(blockId);

        // Action
        final Boolean blockIsValid = blockValidator.validateBlock(blockChainSegmentId, block);

        // Assert
        Assert.assertTrue(blockIsValid);
    }

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

        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

        final BlockInflater blockInflater = new BlockInflater();
        final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));

        final Block block1Prime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain2.BLOCK_1));
        Assert.assertEquals(genesisBlock.getHash(), block1Prime.getPreviousBlockHash());

        final Block block2Prime = blockInflater.fromBytes(HexUtil.hexStringToByteArray("01000000A0725312AADE7F12764C47F9A8B54C4924EE12940C697782AE31F52300000000A66A6AAC381ACE8514F527B6324F72F8ADFBC9B853350675E5309B18E2256E9BA165A45AFFFF001D580D16B802010000000100000000000000000000000000000000000000000000000000000000000000000000000019184D696E65642076696120426974636F696E2D56657264652EFFFFFFFF0100F2052A010000001976A914B8E012A1EC221C31F69AA2895129C02C90AAE2C588AC0000000001000000011D1E1A5147E3170CB7B8DF8B234794CC61B798144B532EEE9C3A62CF9F5A4EBF000000008B483045022100CD4556EFF98614498B736E7894446F82B92638B40512B95C3C64FA06682F68A702200C02B594C919046E6D1EC951745D51778575BDBDB46DCB9793AAC3D0714AAAD10141044B1F57CD308E8AE8AADA38AA8183A348E1F12C107898760A11324E1B0288C49DE1AB5E1DA16F649B7375046E165FD2CF143F08989F1092A7ED6FED183107B236FFFFFFFF0100F2052A010000001976A91476B5CB4E5D485BDD6B6DD7A2AAAEC6F01FFA7DD488AC00000000")); // Spends a transaction within block1DoublePrime...
        Assert.assertEquals(block1Prime.getHash(), block2Prime.getPreviousBlockHash());

        final Block block1DoublePrime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain4.BLOCK_1));
        Assert.assertEquals(genesisBlock.getHash(), block1DoublePrime.getPreviousBlockHash());

        final ReadUncommittedDatabaseConnectionFactory connectionFactory = new ReadUncommittedDatabaseConnectionFactory(_database.getDatabaseConnectionFactory());
        final BlockValidator blockValidator = new BlockValidator(connectionFactory, new FakeNetworkTime(Long.MAX_VALUE));

        {
            final BlockId genesisBlockId = blockDatabaseManager.storeBlock(genesisBlock);
            blockChainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);
            final BlockChainSegmentId genesisBlockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(genesisBlockId);
            Assert.assertTrue(blockValidator.validateBlock(genesisBlockChainSegmentId, genesisBlock));
        }

        {
            final BlockId block1PrimeId = blockDatabaseManager.storeBlock(block1Prime);
            blockChainDatabaseManager.updateBlockChainsForNewBlock(block1Prime);
            final BlockChainSegmentId block1PrimeBlockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(block1PrimeId);
            Assert.assertTrue(blockValidator.validateBlock(block1PrimeBlockChainSegmentId, block1Prime));
        }

        {
            final BlockId block1DoublePrimeId = blockDatabaseManager.storeBlock(block1DoublePrime);
            blockChainDatabaseManager.updateBlockChainsForNewBlock(block1DoublePrime);
            final BlockChainSegmentId block1DoublePrimeBlockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(block1DoublePrimeId);
            Assert.assertTrue(blockValidator.validateBlock(block1DoublePrimeBlockChainSegmentId, block1DoublePrime));
        }

        final BlockChainSegmentId block2PrimeBlockChainSegmentId;
        {
            final BlockId block2Id = blockDatabaseManager.storeBlock(block2Prime); // Should be an invalid block...
            blockChainDatabaseManager.updateBlockChainsForNewBlock(block2Prime);
            block2PrimeBlockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(block2Id);
        }

        // Action
        final Boolean block2PrimeIsValid = blockValidator.validateBlock(block2PrimeBlockChainSegmentId, block2Prime);

        // Assert
        Assert.assertFalse(block2PrimeIsValid);
    }

    @Test
    public void difficulty_should_be_recalculated_every_2016th_block() throws Exception {
        // Setup
        final TimeZone timeZone = TimeZone.getTimeZone("GMT+1:00"); // Rome, Italy; DST
        final long blockHeight = 2016 * 2; // 32256L;

        final Long genesisBlockTimestamp = (DateUtil.datetimeToTimestamp("2009-01-03 18:15:05", timeZone) / 1000L);

        final BlockInflater blockInflater = new BlockInflater();
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
        final ReadUncommittedDatabaseConnectionFactory connectionFactory = new ReadUncommittedDatabaseConnectionFactory(_database.getDatabaseConnectionFactory());
        final BlockValidator blockValidator = new BlockValidator(connectionFactory, new FakeNetworkTime(Long.MAX_VALUE));

        _storeBlocks(1, genesisBlockTimestamp); // Store the genesis block... (Since the genesis-block is considered block-height 0.)

        _storeBlocks(2015, genesisBlockTimestamp + 1L);

        { // Store the block that is 2016 blocks before the firstBlockWithDifficultyAdjustment
            // Block Hash: 000000000FA8BFA0F0DD32F956B874B2C7F1772C5FBEDCB1B35E03335C7FB0A8
            // Block Height: 30240
            // Timestamp: B1512B4B
            // NOTE: This block is the block referenced when calculating the elapsed time since the previous update...
            final String blockData = IoUtil.getResource("/blocks/000000000FA8BFA0F0DD32F956B874B2C7F1772C5FBEDCB1B35E03335C7FB0A8");
            final MutableBlock block30240 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
            block30240.setPreviousBlockHash(blockDatabaseManager.getHeadBlockHash()); // Modify this (real) block so that it is on the same chain as the previous (faked) blocks.
            final BlockId blockId = blockDatabaseManager.storeBlock(block30240);
            blockChainDatabaseManager.updateBlockChainsForNewBlock(block30240);
            final Difficulty blockDifficulty = block30240.getDifficulty();
            Assert.assertEquals(Difficulty.BASE_DIFFICULTY, blockDifficulty);
            Assert.assertEquals(2016, blockDatabaseManager.getBlockHeightForBlockId(blockId).longValue());
        }

        _storeBlocks(2014, (DateUtil.datetimeToTimestamp("2009-12-18 09:56:01", timeZone) / 1000L) + 1);

        final Sha256Hash previousBlockHash;
        // Timestamp: 23EC3A4B
        { // Store the previous block so the firstBlockWithDifficultyAdjustment has the correct hash...
            // Block Hash: 00000000984F962134A7291E3693075AE03E521F0EE33378EC30A334D860034B -> AFB816727FE415551ADBE28BCAF2E3D8ECEE4799B4B07735613B40ED8EAD01BA
            final String blockData = IoUtil.getResource("/blocks/00000000984F962134A7291E3693075AE03E521F0EE33378EC30A334D860034B");
            final MutableBlock previousBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
            previousBlock.setPreviousBlockHash(blockDatabaseManager.getHeadBlockHash()); // Modify this (real) block so that it is on the same chain as the previous (faked) blocks.
            final BlockId blockId = blockDatabaseManager.storeBlock(previousBlock);
            blockChainDatabaseManager.updateBlockChainsForNewBlock(previousBlock);
            final Difficulty blockDifficulty = previousBlock.getDifficulty();
            Assert.assertEquals(Difficulty.BASE_DIFFICULTY, blockDifficulty);
            Assert.assertEquals((blockHeight - 1), blockDatabaseManager.getBlockHeightForBlockId(blockId).longValue());

            previousBlockHash = previousBlock.getHash();
        }

        final Difficulty expectedDifficulty = new ImmutableDifficulty(HexUtil.hexStringToByteArray("00D86A"), Difficulty.BASE_DIFFICULTY_EXPONENT);
        final float expectedDifficultyRatio = 1.18F;
        // Original Block with the first difficulty adjustment: 000000004F2886A170ADB7204CB0C7A824217DD24D11A74423D564C4E0904967 (Block Height: 32256)
        //  blockData is a modified version of this block, so that its previous block hash fits the modified block above.
        final String blockData = IoUtil.getResource("/blocks/000000004F2886A170ADB7204CB0C7A824217DD24D11A74423D564C4E0904967");

        final Block firstBlockWithDifficultyIncrease;
        { // Modify the real block to set the the previousBlockHash to our custom block...
            // This block's is intended to only have its previousBlockHash rewritten while still having a suitable Block Hash.
            //  To accomplish this, this block was re-mined with a nonce and extra-nonce.  Instead of using the bytes directly, the
            //  modified fields are set programmatically to more easily determine what exactly was/wasn't modified.
            final MutableBlock mutableBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));

            mutableBlock.setPreviousBlockHash(previousBlockHash);

            mutableBlock.setNonce(1978210639L);

            { // Append extra nonce to coinbase transaction...
                final CoinbaseTransaction coinbaseTransaction = mutableBlock.getCoinbaseTransaction();
                final MutableCoinbaseTransaction modifiedCoinbaseTransaction = new MutableCoinbaseTransaction(coinbaseTransaction);
                final MutableUnlockingScript mutableCoinbaseScript = new MutableUnlockingScript(modifiedCoinbaseTransaction.getCoinbaseScript());
                final OperationInflater operationInflater = new OperationInflater();
                final Operation operation = operationInflater.fromBytes(MutableByteArray.wrap(HexUtil.hexStringToByteArray("053333313134")));
                mutableCoinbaseScript.addOperation(operation);
                modifiedCoinbaseTransaction.setCoinbaseScript(mutableCoinbaseScript);
                mutableBlock.replaceTransaction(0, modifiedCoinbaseTransaction);
            }

            firstBlockWithDifficultyIncrease = mutableBlock;
            System.out.println(firstBlockWithDifficultyIncrease.getHash());
        }

        final Difficulty blockDifficulty = firstBlockWithDifficultyIncrease.getDifficulty();
        Assert.assertEquals(expectedDifficulty, blockDifficulty);
        Assert.assertEquals(expectedDifficultyRatio, blockDifficulty.getDifficultyRatio().floatValue(), 0.005);

        final BlockId blockId = blockDatabaseManager.storeBlock(firstBlockWithDifficultyIncrease);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(firstBlockWithDifficultyIncrease);
        Assert.assertEquals(blockHeight, blockDatabaseManager.getBlockHeightForBlockId(blockId).longValue());

        final BlockChainSegmentId blockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(blockId);

        // Action
        final Boolean blockIsValid = blockValidator.validateBlock(blockChainSegmentId, firstBlockWithDifficultyIncrease);

        // Assert
        Assert.assertTrue(blockIsValid);
    }

    public static class BlockWithFakeMerkleRoot extends MutableBlock {
        private final MerkleRoot _merkleRoot;

        public BlockWithFakeMerkleRoot(final Block block, final MerkleRoot merkleRoot) {
            super(block);
            _merkleRoot = merkleRoot;
        }

        @Override
        public MerkleRoot getMerkleRoot() {
            return _merkleRoot;
        }
    }
}
