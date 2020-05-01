package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.test.fake.FakeBlockHeaderStub;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.security.util.HashUtil;
import com.softwareverde.util.Util;
import org.junit.Assert;
import org.junit.Test;

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

                final BlockHeader[] preSortedBlockHeaders = MedianBlockHeaderSelector.preOrderBlocks(blockHeaders);
                _validatePreOrdering(preSortedBlockHeaders, blockHeights);
            }
        }

        { // PreSorting will likely never be executed on anything other than 3 headers, but just in case...
            final int[][] extraPermutations = new int[][]{
                {0, 1, 2, 3}, {0, 1, 3, 2}, {0, 2, 1, 3}, {0, 2, 3, 1}, {0, 3, 1, 2}, {0, 3, 2, 1},
                {1, 0, 2, 3}, {1, 0, 3, 2}, {1, 2, 0, 3}, {1, 2, 3, 0}, {1, 3, 0, 2}, {1, 3, 2, 0},
                {2, 1, 0, 3}, {2, 1, 3, 0}, {2, 0, 1, 3}, {2, 0, 3, 1}, {2, 3, 1, 0}, {2, 3, 0, 1},
                {3, 1, 2, 0}, {3, 1, 0, 2}, {3, 2, 1, 0}, {3, 2, 0, 1}, {3, 0, 1, 2}, {3, 0, 2, 1}
            };
            for (final int[] permutation : extraPermutations) {
                final BlockHeader[] blockHeaders = new BlockHeader[4];
                blockHeaders[permutation[0]] = blockHeader_587196;
                blockHeaders[permutation[1]] = blockHeader_587197;
                blockHeaders[permutation[2]] = blockHeader_587198;
                blockHeaders[permutation[3]] = blockHeader_587199;

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
        final FakeDatabaseManager databaseManager = new FakeDatabaseManager();

        final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(databaseManager);
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("0000FF3F396F0BFE2648C252F2E83DA61DB3164E228E9A08699ECC000000000000000000B5F208E22E350C16DA7AF7015FD375ED4CF86DF6833399F46767AE1A5A075B452370055D0B45031889818CB6"));
            databaseManager.registerBlockHeader(blockHeader, 587195L, ChainWork.fromHexString("000000000000000000000000000000000000000000F057FC0614743ACE1E0524"));
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00004020C35BA39E60207C9E9E05AC8569DB177FEDF3BE12473BCB0200000000000000008CD5A7960DCEF83D1052C06F04001BE551A30CDABB56BD910D45D66EE44A8CF9350E045D7B5203182A4FA63D"));
            databaseManager.registerBlockHeader(blockHeader, 587051L, ChainWork.fromHexString("000000000000000000000000000000000000000000F029B70179FA4FAC8588F7"));
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000209A11547FA6AECAAEFCFCAE16CA8920D6A0958889952C29020000000000000000A1DA137F6D29BBA1FDA473292F62EB0613A8EED8ABDD18DF335D0F7601FCE82A030F045D435003187B87D418"));
            databaseManager.registerBlockHeader(blockHeader, 587052L, ChainWork.fromHexString("000000000000000000000000000000000000000000F02A0443D339E6B9A4CCD5"));
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("0000002053C807F29DBFEE3B06D7B3AF9DC26E7AEB4AC8C716CD4D03000000000000000056EEC5C2537DDD1E6C0927FE4FD5B9238E9A7B57F096EF5CCB22E6481C4238D02F0F045DCD4803182F2E01FE"));
            databaseManager.registerBlockHeader(blockHeader, 587053L, ChainWork.fromHexString("000000000000000000000000000000000000000000F02A5235ADD723B435AB4E"));
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("0000802029442002438B76394DB51AD4DFEBE6034FF760CC102B21020000000000000000DB02CFEED980E0B1DE1FBC2F32467C58948162931A119F99384BB8F980B6FC911870055D73440318CC25AE45"));
            databaseManager.registerBlockHeader(blockHeader, 587196L, ChainWork.fromHexString("000000000000000000000000000000000000000000F0584A5FBE03CB7F1CF1FF"));
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000204EC6D6C4A07F0D351A48DAA35E684B2ADC806D3F4C0397000000000000000000C67013C6B366AF5A19A8DB92187427384A097950E9E21B0F4BA3A3E271955ACF2370055DEA3D03188963EFB2"));
            databaseManager.registerBlockHeader(blockHeader, 587197L, ChainWork.fromHexString("000000000000000000000000000000000000000000F0589957593E319806C814"));
        }

        final BlockHeader blockHeader;
        { // Height: 587198
            blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00E0FF3FEA3DA6788DDDFB4BC584A6F342CE5AB6C4C67DDB4319860100000000000000003FB1703177952BCFA51D4AD1A099FE56D5E681266BDF4DAE12941DE9AC685AF3F871055D144703183F87E206"));
            databaseManager.registerBlockHeader(blockHeader, 587198L, ChainWork.fromHexString("000000000000000000000000000000000000000000F058E7722B23D4DA9E5DF1"));
        }

        // Action
        final Difficulty difficulty = difficultyCalculator.calculateRequiredDifficulty(blockHeader);

        // Assert
        Assert.assertEquals(blockHeader.getDifficulty(), difficulty);
    }

    @Test
    public void should_calculate_emergency_difficulty() {

//        {
//            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("0000802029442002438B76394DB51AD4DFEBE6034FF760CC102B21020000000000000000DB02CFEED980E0B1DE1FBC2F32467C58948162931A119F99384BB8F980B6FC911870055D73440318CC25AE45"));
//            databaseManager.registerBlockHeader(blockHeader, 487196L, null);
//        }
//
//        {
//            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000204EC6D6C4A07F0D351A48DAA35E684B2ADC806D3F4C0397000000000000000000C67013C6B366AF5A19A8DB92187427384A097950E9E21B0F4BA3A3E271955ACF2370055DEA3D03188963EFB2"));
//            databaseManager.registerBlockHeader(blockHeader, 487197L, null);
//        }
//
//        {
//            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000200E8030580910AC7F8B9CD43D7E47954BC674719076556D01000000000000000026DAF4F1E4EDA3C462486AB3FAFB41524B967D980E0B016E5C3154B690C744FD25A4BF5959070A18D9252ACB"));
//            databaseManager.registerBlockHeader(blockHeader, 487181L, null);
//        }
//
//        {
//            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000203D6A33B85D679440694FDAB9BD8C633E092C0482AAB11F050000000000000000D005F4D450FA8044C4EB4934CF97700F27B9078745B7AF38BD750E8F2B4BD4A537A4BF5959070A1860C73BB6"));
//            databaseManager.registerBlockHeader(blockHeader, 487182L, null);
//        }
//
//        {
//            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00000020B20BC05C3C7CB920D16B8B8FF2E825DE6AF1DD428629DE090000000000000000C2FBCB6FF985D915DA6ACD876A2257C18201AC08BA18D055FC7D0233399231EA6BA4BF5959070A18B5E1E659"));
//            databaseManager.registerBlockHeader(blockHeader, 487183L, null);
//        }
//
//        {
//            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00000020AB6D9117993419E9B6EBBD8D0FDDE47EF55B7F7F606255070000000000000000DDFE36AA8F8DE82AE6A7A6A8AAFC2351129CC874E32251E468E1F0EC17290A9751A5BF5959070A185CE40B05"));
//            databaseManager.registerBlockHeader(blockHeader, 487184L, null);
//        }
//
//        {
//            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000207C160E3CF3A7F7DE6E5E3934F16C345D293293A52F65F90600000000000000006BC7A3199E3B9A67FDEA6F3DD7B8F2AAF22C37746569B2F01FD0FA6DD181D2CE79A5BF5959070A185CFCF8D9"));
//            databaseManager.registerBlockHeader(blockHeader, 487185L, null);
//        }
//
//        {
//            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00000020970930AB85C2D6047615D255AADF04AE5E4602F1482C69060000000000000000106241BEA7E98AA7BA92131BCBDC59071D6D21370CA8A5377E5B2E8156C172E656A6BF5959070A18F7ED0654"));
//            databaseManager.registerBlockHeader(blockHeader, 487186L, null);
//        }
//
//        {
//            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000203AD535A1EE5F933BEE779600837C9BB705EEB3483C326C0900000000000000009BDFC42FD38D7D1AC4A2D3B308C68E567455F0457B56833A090FC3A27233F10341A6BF5959070A183209ADD2"));
//            databaseManager.registerBlockHeader(blockHeader, 487187L, null);
//        }
//
//        {
//            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00000020B8C2A9FD684B59FF1E6A403CEE2597B2E2C01BC0A6F16F090000000000000000833F2E568FACD956043A3D7D0DCC44E1D47D903202FF0000315069F5BBD6FCE65AA6BF5959070A18B8C6561B"));
//            databaseManager.registerBlockHeader(blockHeader, 487188L, null);
//        }
//
//        {
//            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000201499AD859A9F20738047C583EE3D70AA9B0A43C5E2787C0400000000000000001259E53B36979E9EF3E82E312C24D6830BF0BC7108CCC3279138D8D62A2B15AEFEA6BF5959070A1868C159D1"));
//            databaseManager.registerBlockHeader(blockHeader, 487189L, null);
//        }
//
//        {
//            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00000020667C5C701D4CD746C65CE3E0A0B72F88EE0E89C077285C05000000000000000042FD07BBA2B423600F96A31F9B137792436D338B750BEB7A73ECB7B1A107D6DBC7A7BF5959070A189E1F64AC"));
//            databaseManager.registerBlockHeader(blockHeader, 487190L, null);
//        }
//
//        {
//            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000204C6DA86DE6D3609D667AEEF26B0CAE2E08D28FAC22104700000000000000000050C4BCE1EE2559E703302002C9A978E3293D94D06717955FFF5D44AA1D670F9341A9BF5959070A180AB22D0D"));
//            databaseManager.registerBlockHeader(blockHeader, 487191L, null);
//        }
//
//        {
//            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("0000002010CA0369BDB392B00E48A26E7297328D76F194B77F1064020000000000000000D99D1534FD63E87E0D5612BE3C3CBE51C923346E3053A923CBDE416556EDEEFB2D6C055D34510318CF7CCBB1"));
//            databaseManager.registerBlockHeader(blockHeader, 587187L, null);
//        }
//
//        {
//            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00000020DFF37A1C662C4A3276C0653147A7B12417770F676DECDE000000000000000000E9A3D84E2660F026196820A2CDD80F1D95BF085E031D530ED1F1FEA2EF782EA6EB6C055DBC51031879511BEB"));
//            databaseManager.registerBlockHeader(blockHeader, 587188L, null);
//        }
//
//        {
//            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("0000C020848A1F1662D218B9C061AB41595CD0B161E046B3C69B2C0300000000000000003361EC7AB0B32EC70659A63DA6E1F8CA944EA219E13745C3E422A2D47BFA0568FE6D055D93520318C0F46A2D"));
//            databaseManager.registerBlockHeader(blockHeader, 587189L, null);
//        }
//
//        {
//            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00000020C1467E46D4E781856B255F844167D7AD41985F14038BF902000000000000000016019649B5EB3A7AF8EAD6BCFA1706DAE53C98111B15A567E4046163713C7128306E055DB6520318114AC70E"));
//            databaseManager.registerBlockHeader(blockHeader, 587190L, null);
//        }
//
//        {
//            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("0000C020993AFB91A3DA06E8588ED7A2BEDF7EF580BD59139490EA000000000000000000228F3BECC2005B8252A843FA7614AB5886D848706CDBB744DF74F2636A0492F2936E055D71520318041D98D1"));
//            databaseManager.registerBlockHeader(blockHeader, 587191L, null);
//        }
//
//        {
//            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00E00020CA3DD2220A79603CC01B5020AC742814696D6A5FDF5D2A0200000000000000003B169B33A91D61AE4EBA1E792FB1C6B8776626E6F398838A12BA991AE45AEFACD66E055D604703182F9A6631"));
//            databaseManager.registerBlockHeader(blockHeader, 587192L, null);
//        }
//
//        {
//            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000209BAA890C096E7D25533EB6D83DE19F0AB48DF9214216CB080000000000000000D6352EAA8E8D8EABBBFD7006A5AB1916350981AD3158805C3BA91189020BAA688FA9BF5959070A1822984846"));
//            databaseManager.registerBlockHeader(blockHeader, 487192L, null);
//        }
//
//        {
//            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("0000002037C3455AA34BA4B325339A38AD985FA773E3CAE4CD052A01000000000000000049F464F13D452661A86566BE978A8C1C07E263E57F29B5C48E965765EE93246D8B6F055D9C460318913BF6B5"));
//            databaseManager.registerBlockHeader(blockHeader, 587193L, null);
//        }
//
//        {
//            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("0000FF3F4D4411F9E752E66CE1456860F57B9067870675910AC6F7000000000000000000B141E85299981AA63E6B6E4FAE213FA868B51493F2BFB89A558616FBF926B7D8A76F055D6E450318D6F88D95"));
//            databaseManager.registerBlockHeader(blockHeader, 587194L, null);
//        }
//

        Assert.fail(); // TODO
    }
}