package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.bip.CoreUpgradeSchedule;
import com.softwareverde.bitcoin.bip.TestNetUpgradeSchedule;
import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.header.MutableBlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.block.validator.BlockHeaderValidator;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.DifficultyCalculatorContext;
import com.softwareverde.bitcoin.context.core.AsertReferenceBlockLoader;
import com.softwareverde.bitcoin.merkleroot.ImmutableMerkleRoot;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.test.fake.FakeBlockHeaderStub;
import com.softwareverde.bitcoin.test.fake.FakeBlockHeaderValidatorContext;
import com.softwareverde.bitcoin.test.fake.FakeDifficultyCalculatorContext;
import com.softwareverde.bitcoin.test.fake.FakeReferenceBlockLoaderContext;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.Util;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

public class DifficultyCalculatorUnitTests extends UnitTest {

    protected void _validatePreOrdering(final BlockHeader[] blockHeaders, final Map<Sha256Hash, Long> blockHeights) {
        Assert.assertNotNull(blockHeaders);

        Assert.assertEquals(Long.valueOf(587196L), blockHeights.get(blockHeaders[0].getHash()));
        Assert.assertEquals(Long.valueOf(587197L), blockHeights.get(blockHeaders[1].getHash()));
        Assert.assertEquals(Long.valueOf(587198L), blockHeights.get(blockHeaders[2].getHash()));

        if (blockHeaders.length > 3) {
            Assert.assertEquals(Long.valueOf(587199L), blockHeights.get(blockHeaders[3].getHash()));
        }
    }

    @Test
    public void should_pre_order_blocks_before_sorting() {
        // Setup
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();

        final BlockHeader blockHeader_587196 = blockHeaderInflater.fromBytes(ByteArray.fromHexString("0000802029442002438B76394DB51AD4DFEBE6034FF760CC102B21020000000000000000DB02CFEED980E0B1DE1FBC2F32467C58948162931A119F99384BB8F980B6FC911870055D73440318CC25AE45"));
        final BlockHeader blockHeader_587197 = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000204EC6D6C4A07F0D351A48DAA35E684B2ADC806D3F4C0397000000000000000000C67013C6B366AF5A19A8DB92187427384A097950E9E21B0F4BA3A3E271955ACF2370055DEA3D03188963EFB2"));
        final BlockHeader blockHeader_587198 = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00E0FF3FEA3DA6788DDDFB4BC584A6F342CE5AB6C4C67DDB4319860100000000000000003FB1703177952BCFA51D4AD1A099FE56D5E681266BDF4DAE12941DE9AC685AF3F871055D144703183F87E206"));
        final BlockHeader blockHeader_587199 = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000208D950B2E905F563F7DD5B268218FC71A5650BCB6A06EB601000000000000000062FAE64FAFFBCE8F47250501AF1C9DEF00585B4668DA8A0B2E635DFC02A5B2CA2972055D0D41031882224C0B"));

        final HashMap<Sha256Hash, Long> blockHeights = new HashMap<Sha256Hash, Long>(3);
        blockHeights.put(blockHeader_587196.getHash(), 587196L);
        blockHeights.put(blockHeader_587197.getHash(), 587197L);
        blockHeights.put(blockHeader_587198.getHash(), 587198L);
        blockHeights.put(blockHeader_587199.getHash(), 587199L);

        { // Ensure all possible permutations for a 3-header array succeed...
            final int[][] permutations = new int[][]{
                {0, 1, 2}, {0, 2, 1},
                {1, 0, 2}, {1, 2, 0},
                {2, 0, 1}, {2, 1, 0}
            };
            for (final int[] permutation : permutations) {
                final BlockHeader[] blockHeaders = new BlockHeader[3];
                blockHeaders[permutation[0]] = blockHeader_587196;
                blockHeaders[permutation[1]] = blockHeader_587197;
                blockHeaders[permutation[2]] = blockHeader_587198;

                // System.out.println("Test: {" + permutation[0] + "," + permutation[1] + "," + permutation[2] + "}");
                final BlockHeader[] preSortedBlockHeaders = MedianBlockHeaderSelector.preOrderBlocks(blockHeaders);
                _validatePreOrdering(preSortedBlockHeaders, blockHeights);
            }
        }

        { // Ensure unrelated headers preSort to null...
            final BlockHeader[] blockHeaders = new BlockHeader[3];
            blockHeaders[0] = blockHeader_587196;
            blockHeaders[1] = blockHeader_587197;
            blockHeaders[2] = blockHeader_587199;

            final BlockHeader[] preSortedBlockHeaders = MedianBlockHeaderSelector.preOrderBlocks(blockHeaders);
            Assert.assertNull(preSortedBlockHeaders);
        }
    }

    static class FakeBlockHeader extends FakeBlockHeaderStub {
        protected final Long _blockHeight;
        protected final Sha256Hash _hash;
        protected final Sha256Hash _previousBlockHash;
        protected Long _timestamp = 0L;

        public FakeBlockHeader(final Long blockHeight) {
            _blockHeight = blockHeight;
            _hash = Sha256Hash.wrap(HashUtil.sha256(ByteUtil.longToBytes(blockHeight)));
            _previousBlockHash = Sha256Hash.wrap(HashUtil.sha256(ByteUtil.longToBytes(blockHeight - 1L)));
        }

        public FakeBlockHeader(final Long blockHeight, final Long timestamp) {
            _blockHeight = blockHeight;
            _hash = Sha256Hash.wrap(HashUtil.sha256(ByteUtil.longToBytes(blockHeight)));
            _previousBlockHash = Sha256Hash.wrap(HashUtil.sha256(ByteUtil.longToBytes(blockHeight - 1L)));
            _timestamp = timestamp;
        }

        @Override
        public Sha256Hash getHash() {
            return _hash;
        }

        @Override
        public Sha256Hash getPreviousBlockHash() {
            return _previousBlockHash;
        }

        @Override
        public Long getTimestamp() {
            return _timestamp;
        }

        public Long getBlockHeight() {
            return _blockHeight;
        }

        public void setTimestamp(final Long timestamp) {
            _timestamp = timestamp;
        }

        @Override
        public boolean equals(final Object object) {
            if (! (object instanceof BlockHeader)) { return false; }
            final BlockHeader blockHeader = (BlockHeader) object;
            return Util.areEqual(_hash, blockHeader.getHash());
        }
    }

    /**
     * This test asserts that the block-selection algorithm for the new Bitcoin Cash Difficulty Adjustment Algorithm
     *  selects the same "median" block when 2 of the 3 sequenced blocks share the same timestamp.
     *  Reference: https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/nov-13-hardfork-spec.md#footnotes
     *      """A block is chosen via the following mechanism:
     *          Given a list: S = [B_n-2, B_n-1, B_n]
     *              a. If timestamp(S[0]) greater than timestamp(S[2]) then swap S[0] and S[2].
     *              b. If timestamp(S[0]) greater than timestamp(S[1]) then swap S[0] and S[1].
     *              c. If timestamp(S[1]) greater than timestamp(S[2]) then swap S[1] and S[2].
     *              d. Return S[1].
     *      """
     */
    @Test
    public void should_select_block_header_based_on_insert_order_when_timestamps_are_identical() {
        final MedianBlockHeaderSelector medianBlockHeaderSelector = new MedianBlockHeaderSelector();

        { // The first three elements per permutation are the timestamps, the fourth element is the index expected to be selected (which is also the fake blockHeight)...
            final int[][] timestampsAndExpectedHeight = new int[][]{
                {0, 0, 0,  1}, {0, 0, 1,  1}, {0, 1, 0,  2}, {0, 1, 1,  1},
                {1, 0, 0,  1}, {1, 0, 1,  0}, {1, 1, 0,  1}, {1, 1, 1,  1}
            };
            for (final int[] permutation : timestampsAndExpectedHeight) {
                final BlockHeader[] blockHeaders = new BlockHeader[3];
                blockHeaders[0] = new FakeBlockHeader(0L, (long) permutation[0]);
                blockHeaders[1] = new FakeBlockHeader(1L, (long) permutation[1]);
                blockHeaders[2] = new FakeBlockHeader(2L, (long) permutation[2]);

                final BlockHeader expectedBlockHeader = new FakeBlockHeader((long) permutation[3]);
                final BlockHeader blockHeader = medianBlockHeaderSelector.selectMedianBlockHeader(blockHeaders);
                // System.out.println("Timestamps={" + permutation[0] + "," + permutation[1] + "," + permutation[2] + "} -> " + ((FakeBlockHeader) blockHeader).getBlockHeight());
                Assert.assertEquals(expectedBlockHeader, blockHeader);
            }
        }
    }

    @Test
    public void should_calculate_difficulty_for_block_000000000000000001B66EA0B6BC50561AC78F2168B2D57D3F565F902E0B958D() {
        // Setup
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();

        final HashMap<Long, BlockHeader> blockHeaders = new HashMap<Long, BlockHeader>();
        final HashMap<Long, ChainWork> chainWorks = new HashMap<Long, ChainWork>();
        final HashMap<Long, MedianBlockTime> medianBlockTimes = new HashMap<Long, MedianBlockTime>();

        {
            final Long blockHeight = 587051L;
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00004020C35BA39E60207C9E9E05AC8569DB177FEDF3BE12473BCB0200000000000000008CD5A7960DCEF83D1052C06F04001BE551A30CDABB56BD910D45D66EE44A8CF9350E045D7B5203182A4FA63D"));
            blockHeaders.put(blockHeight, blockHeader);
            chainWorks.put(blockHeight, ChainWork.fromHexString("000000000000000000000000000000000000000000F029B70179FA4FAC8588F7"));
        }

        {
            final Long blockHeight = 587052L;
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000209A11547FA6AECAAEFCFCAE16CA8920D6A0958889952C29020000000000000000A1DA137F6D29BBA1FDA473292F62EB0613A8EED8ABDD18DF335D0F7601FCE82A030F045D435003187B87D418"));
            blockHeaders.put(blockHeight, blockHeader);
            chainWorks.put(blockHeight, ChainWork.fromHexString("000000000000000000000000000000000000000000F02A0443D339E6B9A4CCD5"));
        }

        {
            final Long blockHeight = 587053L;
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("0000002053C807F29DBFEE3B06D7B3AF9DC26E7AEB4AC8C716CD4D03000000000000000056EEC5C2537DDD1E6C0927FE4FD5B9238E9A7B57F096EF5CCB22E6481C4238D02F0F045DCD4803182F2E01FE"));
            blockHeaders.put(blockHeight, blockHeader);
            chainWorks.put(blockHeight, ChainWork.fromHexString("000000000000000000000000000000000000000000F02A5235ADD723B435AB4E"));
        }

        {
            final Long blockHeight = 587195L;
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("0000FF3F396F0BFE2648C252F2E83DA61DB3164E228E9A08699ECC000000000000000000B5F208E22E350C16DA7AF7015FD375ED4CF86DF6833399F46767AE1A5A075B452370055D0B45031889818CB6"));
            blockHeaders.put(blockHeight, blockHeader);
            chainWorks.put(blockHeight, ChainWork.fromHexString("000000000000000000000000000000000000000000F057FC0614743ACE1E0524"));
        }

        {
            final Long blockHeight = 587196L;
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("0000802029442002438B76394DB51AD4DFEBE6034FF760CC102B21020000000000000000DB02CFEED980E0B1DE1FBC2F32467C58948162931A119F99384BB8F980B6FC911870055D73440318CC25AE45"));
            blockHeaders.put(blockHeight, blockHeader);
            chainWorks.put(blockHeight, ChainWork.fromHexString("000000000000000000000000000000000000000000F0584A5FBE03CB7F1CF1FF"));
        }

        {
            final Long blockHeight = 587197L;
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000204EC6D6C4A07F0D351A48DAA35E684B2ADC806D3F4C0397000000000000000000C67013C6B366AF5A19A8DB92187427384A097950E9E21B0F4BA3A3E271955ACF2370055DEA3D03188963EFB2"));
            blockHeaders.put(blockHeight, blockHeader);
            chainWorks.put(blockHeight, ChainWork.fromHexString("000000000000000000000000000000000000000000F0589957593E319806C814"));
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(1560637142L));
        }

        final BlockHeader blockHeader;
        { // Height: 587198
            final Long blockHeight = 587198L;
            blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00E0FF3FEA3DA6788DDDFB4BC584A6F342CE5AB6C4C67DDB4319860100000000000000003FB1703177952BCFA51D4AD1A099FE56D5E681266BDF4DAE12941DE9AC685AF3F871055D144703183F87E206"));
            blockHeaders.put(blockHeight, blockHeader);
            chainWorks.put(blockHeight, ChainWork.fromHexString("000000000000000000000000000000000000000000F058E7722B23D4DA9E5DF1"));
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(1560637323L));
        }

        final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();
        final DifficultyCalculatorContext difficultyCalculatorContext = new DifficultyCalculatorContext() {
            @Override
            public DifficultyCalculator newDifficultyCalculator() {
                return new DifficultyCalculator(this);
            }

            @Override
            public AsertReferenceBlock getAsertReferenceBlock() {
                return null;
            }

            @Override
            public BlockHeader getBlockHeader(final Long blockHeight) {
                if (! blockHeaders.containsKey(blockHeight)) {
                    Assert.fail("Requesting unregistered BlockHeader for blockHeight: " + blockHeight);
                }
                return blockHeaders.get(blockHeight);
            }

            @Override
            public ChainWork getChainWork(final Long blockHeight) {
                if (! chainWorks.containsKey(blockHeight)) {
                    Assert.fail("Requesting unregistered ChainWork for blockHeight: " + blockHeight);
                }
                return chainWorks.get(blockHeight);
            }

            @Override
            public MedianBlockTime getMedianBlockTime(final Long blockHeight) {
                if (! medianBlockTimes.containsKey(blockHeight)) {
                    Assert.fail("Requesting unregistered MedianBlockTime for blockHeight: " + blockHeight);
                }
                return medianBlockTimes.get(blockHeight);
            }

            @Override
            public UpgradeSchedule getUpgradeSchedule() {
                return upgradeSchedule;
            }
        };

        final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(difficultyCalculatorContext);

        // Action
        final Difficulty difficulty = difficultyCalculator.calculateRequiredDifficulty(587198L);

        // Assert
        Assert.assertEquals(blockHeader.getDifficulty(), difficulty);
    }

    @Test
    public void should_calculate_emergency_difficulty() {
        // Setup
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
        final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();
        final FakeDifficultyCalculatorContext difficultyCalculatorContext = new FakeDifficultyCalculatorContext(upgradeSchedule);
        final HashMap<Long, BlockHeader> blockHeaders = difficultyCalculatorContext.getBlockHeaders();
        final HashMap<Long, MedianBlockTime> medianBlockTimes = difficultyCalculatorContext.getMedianBlockTimes();

        {
            final Long blockHeight = 499962L;
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(1509240003L));
        }

        {
            final Long blockHeight = 499968L;
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00000020C989289B55C9ED4D296DCD6F459726C9B28AE8D6098F8C09000000000000000051D0E4A45E846EA18B3CED0BA2C5585D2C7312005F2424FE92C243491082F82C2135F5598AFB03184C8D2FE2"));
            blockHeaders.put(blockHeight, blockHeader);
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(1509240426L));
        }

        final Long blockHeight = 499969L;
        final BlockHeader blockHeader;
        {
            blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00000020F779752E58AA1CDB2CD2F59BFE69D4B327886D2CF43FAA030000000000000000CD2F883DC10C2140CCB5A12E84F188924BF209902FEB31191F60AA318A0D9068EB36F5598AFB031896833D85"));
            blockHeaders.put(blockHeight, blockHeader);
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(1509240436L));
        }

        final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(difficultyCalculatorContext);

        { // Ensure the Difficulty calculation is using the emergency calculation...
            Assert.assertNotEquals(0L, (blockHeight % DifficultyCalculator.BLOCK_COUNT_PER_DIFFICULTY_ADJUSTMENT)); // Legacy Difficulty adjustment...
        }

        // Action
        final Difficulty difficulty = difficultyCalculator.calculateRequiredDifficulty(blockHeight);

        // Assert
        Assert.assertEquals(blockHeader.getDifficulty(), difficulty);
    }

    @Test
    public void should_calculate_difficulty_for_block_0000000000000000037DF1664BEE98AF9EF9BBCDEF882FDF16F91C6EC94334DF() {
        // Setup
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
        final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();
        final FakeBlockHeaderValidatorContext context = new FakeBlockHeaderValidatorContext(new MutableNetworkTime(), upgradeSchedule);
        final HashMap<Long, BlockHeader> blockHeaders = context.getBlockHeaders();
        final HashMap<Long, MedianBlockTime> medianBlockTimes = context.getMedianBlockTimes();

        {
            final long blockHeight = 478575L;
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000205841AEA6F06F4A0E3560EA076CF1DF217EBDE943A92C16010000000000000000CF8FC3BAD8DAD139A3DD6A30481D87E1F760122573168002CC9EF7A58FC53AD387848259354701188A3B54F7"));
            blockHeaders.put(blockHeight, blockHeader);
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(1501643701L));
        }

        {
            final long blockHeight = 478581L;
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00000020F3C6E921BB46DE2EBE0923C6FEF9433510F57E15543D1301000000000000000071CCD70BC227084B74BDA5634BE313C63C213F46AB8801411CB78809F8408C3D81C182598BE6031894141AA2"));
            blockHeaders.put(blockHeight, blockHeader);
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(1501727260L));
        }

        final Long blockHeight = 478582L;
        final BlockHeader blockHeader;
        {
            blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000201C9C41FFE35BA49571E759BAD99C416F48594C8BEB7EA5030000000000000000FF562FBEAD0871D7ED63B2BCF2E8D49434980E5CEF8F72C399CCFD6C07BEC8E76FCE82592DE00418F963C170"));
            blockHeaders.put(blockHeight, blockHeader);
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(1501730885L));
        }

        final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(context);

        { // Ensure the Difficulty calculation is using the emergency calculation...
            Assert.assertNotEquals(0L, (blockHeight % DifficultyCalculator.BLOCK_COUNT_PER_DIFFICULTY_ADJUSTMENT)); // Legacy Difficulty adjustment...
        }

        final BlockHeaderValidator blockHeaderValidator = new BlockHeaderValidator(context);

        // Action
        final Difficulty difficulty = difficultyCalculator.calculateRequiredDifficulty(blockHeight);
        final BlockHeaderValidator.BlockHeaderValidationResult blockHeaderValidationResult = blockHeaderValidator.validateBlockHeader(blockHeader, blockHeight);

        // Assert
        Assert.assertTrue(difficulty.isSatisfiedBy(blockHeader.getHash()));
        Assert.assertEquals(difficulty, blockHeader.getDifficulty());
        System.out.println(blockHeaderValidationResult.errorMessage);
        Assert.assertTrue(blockHeaderValidationResult.isValid);
    }

    @Test
    public void should_activate_testnet_headers() {
        // Setup
        final Json headersObjectJson = Json.parse(IoUtil.getResource("/aserti3-2d/test-net-headers.json"));

        final BlockchainSegmentId blockchainSegmentId = BlockchainSegmentId.wrap(1L);
        final UpgradeSchedule upgradeSchedule = new TestNetUpgradeSchedule() {
            @Override
            public Boolean isAsertDifficultyAdjustmentAlgorithmEnabled(final MedianBlockTime medianBlockTime) {
                if (medianBlockTime == null) { return true; }

                final long currentTimestamp = medianBlockTime.getCurrentTimeInSeconds();
                return (currentTimestamp >= 1603393200L); // this was the testing testnet activation time (and headers)
            }
        };

        final FakeReferenceBlockLoaderContext referenceBlockLoaderContext = new FakeReferenceBlockLoaderContext(upgradeSchedule);
        final FakeDifficultyCalculatorContext difficultyCalculatorContext = new FakeDifficultyCalculatorContext(upgradeSchedule) {
            final AsertReferenceBlockLoader _asertReferenceBlockLoader = new AsertReferenceBlockLoader(referenceBlockLoaderContext);

            @Override
            public AsertReferenceBlock getAsertReferenceBlock() {
                try {
                    return _asertReferenceBlockLoader.getAsertReferenceBlock(blockchainSegmentId);
                }
                catch (final Exception exception) {
                    throw new RuntimeException(exception);
                }
            }
        };
        final HashMap<Long, BlockHeader> blockHeaders = difficultyCalculatorContext.getBlockHeaders();
        final HashMap<Long, MedianBlockTime> medianBlockTimes = difficultyCalculatorContext.getMedianBlockTimes();
        final HashMap<Long, ChainWork> chainWorks = difficultyCalculatorContext.getChainWorks();

        { // Inflate the context from the testnet json resource...
            final int headerCount = headersObjectJson.length();
            for (int i = 0; i < headerCount; ++i) {
                final Json json = headersObjectJson.get(i);

                final Long blockHeight = json.getLong("height");
                final Long blockTimestamp = json.getLong("time");

                final MutableBlockHeader blockHeader = new MutableBlockHeader();
                blockHeader.setVersion(json.getLong("version"));
                blockHeader.setTimestamp(blockTimestamp);
                blockHeader.setNonce(json.getLong("nonce"));

                final ByteArray difficultyBytes = ByteArray.fromHexString(json.getString("bits"));
                final Difficulty difficulty = Difficulty.decode(difficultyBytes);
                blockHeader.setDifficulty(difficulty);

                final MerkleRoot merkleRoot = ImmutableMerkleRoot.fromHexString(json.getString("merkleroot"));
                blockHeader.setMerkleRoot(merkleRoot);

                final Sha256Hash previousBlockHash = Sha256Hash.fromHexString(json.getString("previousblockhash"));
                blockHeader.setPreviousBlockHash(previousBlockHash);

                final Sha256Hash expectedBlockHash = Sha256Hash.fromHexString(json.getString("hash"));

                final Sha256Hash blockHash = blockHeader.getHash();
                Assert.assertEquals(expectedBlockHash, blockHash);

                // BlockHeader
                blockHeaders.put(blockHeight, blockHeader);

                // ChainWork
                final ChainWork chainWork = ChainWork.fromHexString(json.getString("chainwork"));
                chainWorks.put(blockHeight, chainWork);

                // MedianTimePast
                final MedianBlockTime medianTimePast = MedianBlockTime.fromSeconds(json.getLong("mediantime"));
                medianBlockTimes.put(blockHeight, medianTimePast);

                // AsertReferenceBlockLoader
                final BlockId blockId = BlockId.wrap(blockHeight + 1L);
                referenceBlockLoaderContext.setBlockHeader(blockchainSegmentId, blockId, blockHeight, medianTimePast, blockTimestamp, difficulty);

                // Debug
                Logger.debug("TestNet Block: " + blockHeight + ": " + blockHash + " " + difficultyBytes + " " + medianTimePast + " " + chainWork);
            }
        }

        final DifficultyCalculator difficultyCalculator = new TestNetDifficultyCalculator(difficultyCalculatorContext);

        final Long blockHeight = 1416399L;

        // Action
        final AsertReferenceBlock asertReferenceBlock = difficultyCalculatorContext.getAsertReferenceBlock();
        final Difficulty difficulty = difficultyCalculator.calculateRequiredDifficulty(blockHeight);

        // Assert
        Assert.assertEquals(BigInteger.valueOf(1416397L), asertReferenceBlock.blockHeight);

        final BlockHeader blockHeader = blockHeaders.get(blockHeight);
        Assert.assertEquals(blockHeader.getDifficulty(), difficulty);
    }
}
