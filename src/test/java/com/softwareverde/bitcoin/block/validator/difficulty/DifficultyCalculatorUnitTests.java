package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.header.MutableBlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.fullnode.FullNodeBlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.PendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.node.BitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.test.fake.database.FakeBlockHeaderDatabaseManager;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;

class FakeDatabaseManager implements com.softwareverde.bitcoin.test.fake.database.FakeDatabaseManager {
    protected static final BlockchainSegmentId BLOCKCHAIN_SEGMENT_ID = BlockchainSegmentId.wrap(1L);
    protected Long _nextBlockId = 1L;
    protected final HashMap<Sha256Hash, BlockId> _blockIds = new HashMap<Sha256Hash, BlockId>();
    protected final HashMap<BlockId, BlockHeader> _blockHeaders = new HashMap<BlockId, BlockHeader>();
    protected final HashMap<BlockId, Long> _blockHeights = new HashMap<BlockId, Long>();
    protected final HashMap<Long, BlockId> _blocksByBlockHeight = new HashMap<Long, BlockId>();
    protected final HashMap<BlockId, ChainWork> _chainWork = new HashMap<BlockId, ChainWork>();

    @Override
    public BlockHeaderDatabaseManager getBlockHeaderDatabaseManager() {
        return new FakeBlockHeaderDatabaseManager() {
            @Override
            public Sha256Hash getBlockHash(final BlockId blockId) throws DatabaseException {
                if (! _blockHeaders.containsKey(blockId)) {
                    Logger.debug("Requested unregistered BlockHeader. blockId=" + blockId);
                }

                final BlockHeader blockHeader = _blockHeaders.get(blockId);
                return blockHeader.getHash();
            }

            @Override
            public BlockchainSegmentId getBlockchainSegmentId(final BlockId blockId) {
                if (! _blockIds.containsKey(blockId)) {
                    return null;
                }

                return BLOCKCHAIN_SEGMENT_ID;
            }

            @Override
            public BlockId getBlockHeaderId(final Sha256Hash blockHash) {
                if (! _blockIds.containsKey(blockHash)) {
                    Logger.debug("Requested unregistered BlockId. blockHash=" + blockHash);
                }

                return _blockIds.get(blockHash);
            }

            @Override
            public Long getBlockHeight(final BlockId blockId) {
                if (! _blockHeights.containsKey(blockId)) {
                    Logger.debug("Requested unregistered BlockHeight. blockId=" + blockId);
                }

                return _blockHeights.get(blockId);
            }

            @Override
            public BlockId getAncestorBlockId(final BlockId blockId, final Integer parentCount) {
                final Long blockHeight = _blockHeights.get(blockId);
                if (! _blockHeights.containsKey(blockId)) {
                    Logger.debug("Requested unregistered BlockId. blockId=" + blockId);
                }

                final Long requestedBlockHeight = (blockHeight - parentCount);

                if (! _blocksByBlockHeight.containsKey(requestedBlockHeight)) {
                    Logger.debug("Requested unregistered BlockHeight. blockHeight=" + requestedBlockHeight);
                }

                return _blocksByBlockHeight.get(requestedBlockHeight);
            }

            @Override
            public BlockId getBlockIdAtHeight(final BlockchainSegmentId blockchainSegmentId, final Long blockHeight) {
                if (! Util.areEqual(blockchainSegmentId, BLOCKCHAIN_SEGMENT_ID)) { return null; }

                if (! _blocksByBlockHeight.containsKey(blockHeight)) {
                    Logger.debug("Requested unregistered BlockId. blockHeight=" + blockHeight);
                }

                return _blocksByBlockHeight.get(blockHeight);
            }

            @Override
            public BlockHeader getBlockHeader(final BlockId blockId) {
                if (! _blockHeaders.containsKey(blockId)) {
                    Logger.debug("Requested unregistered BlockHeader. blockId=" + blockId);
                }

                return _blockHeaders.get(blockId);
            }

            @Override
            public ChainWork getChainWork(final BlockId blockId) {
                if (! _chainWork.containsKey(blockId)) {
                    Logger.debug("Requested unregistered ChainWork. blockId=" + blockId);
                }

                return _chainWork.get(blockId);
            }

            @Override
            public MedianBlockTime calculateMedianBlockTimeStartingWithBlock(final BlockId blockId) throws DatabaseException {
                final Sha256Hash blockHash = this.getBlockHash(blockId);
                return FakeBlockHeaderDatabaseManager.newInitializedMedianBlockTime(this, blockHash);
            }

            @Override
            public MedianBlockTime calculateMedianBlockTime(final BlockId blockId) throws DatabaseException {
                final BlockId previousBlockId = this.getAncestorBlockId(blockId, 1);
                final Sha256Hash blockHash = this.getBlockHash(previousBlockId);
                return FakeBlockHeaderDatabaseManager.newInitializedMedianBlockTime(this, blockHash);
            }
        };
    }

    public void registerBlockHeader(final BlockHeader blockHeader, final Long blockHeight, final ChainWork chainWork) {
        final Sha256Hash blockHash = blockHeader.getHash();

        final BlockId blockId = BlockId.wrap(_nextBlockId);
        _blockIds.put(blockHash, blockId);
        _blockHeights.put(blockId, blockHeight);
        _blockHeaders.put(blockId, blockHeader.asConst());
        _blocksByBlockHeight.put(blockHeight, blockId);
        _chainWork.put(blockId, chainWork);

        _nextBlockId += 1L;
    }
}

public class DifficultyCalculatorUnitTests extends UnitTest {

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
        // Setup
        final FakeDatabaseManager databaseManager = new FakeDatabaseManager();

        final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(databaseManager);
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();

        ChainWork chainWork;

        // Starting Blocks (547057 through 547059)
        chainWork = ChainWork.fromHexString("000000000000000000000000000000000000000000C06C4B44874C9B9A130D94"); // 547056

        final BlockHeader block547057 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020B5FBB21B15194519A93E89B3C9E4DCDE7A30E3378065540000000000000000002307AE41F518AB7B1ADCEC650A66DFA6D2805C0C583D5D74A9A6E4D8C71B52B4309A945B211902181E80E9F6"));
        final BlockHeader block547058 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0000002087D4725EF111A4C59AD8F4324A41C034AA089B0268D733010000000000000000CD99207673166CF38726CED59FD0902802935C6C6B67B80E17A976009340DFCC4C9C945B88180218867927A5"));
        final MutableBlockHeader block547059 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020F54AF12DFB380656BF448C8AE3A20151C56A312B9DE9790100000000000000008F9A5ABD96F5D9731CCDEDBC978169378178557031E8F0BD0F3AE87070848EFF499D945B7F2102180C04F854"));

        // IMPORTANT: This forces 57 and 59 to have the same timestamp, which then changes the selected block from 58 to 57.
        block547059.setTimestamp(block547057.getTimestamp());

        // 547057
        chainWork = ChainWork.add(chainWork, block547057.getDifficulty().calculateWork());
        databaseManager.registerBlockHeader(block547057, 547057L, chainWork);

        // 547058 (The "first" selected block...)
        chainWork = ChainWork.add(chainWork, block547058.getDifficulty().calculateWork()); // Unused...
        databaseManager.registerBlockHeader(block547058, 547058L, null); // ChainWork is null in order to ensure the difficulty calculation fails if this block is selected...

        // 547059
        chainWork = ChainWork.add(chainWork, block547059.getDifficulty().calculateWork()); // Unused...
        databaseManager.registerBlockHeader(block547059, 547059L, null); // ChainWork is null in order to ensure the difficulty calculation fails if this block is selected...

        // Ending Blocks (547201 through 547203)
        chainWork = ChainWork.fromHexString("000000000000000000000000000000000000000000C0B356BB448CE8066B2F93"); // 547200

        final BlockHeader block547201 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020DEBBC302DBED222D0FF299D1FDFC2667ED9CB5DCAC3B040100000000000000000DBFBF361ADC14E26962F130A465AF6E4D68746AF94F38F2E783ACC8C094DE651EFA955B982302189EC47A14"));
        final BlockHeader block547202 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0000002030C831E27B35C498552F087186351E8A55DC1F1D83CF49010000000000000000CD7611E790C1051D5FC2E024EEAFD7A691DD3B92167211BEAFAE161FAC030D972DFC955B0F240218F86BB4D6"));
        final MutableBlockHeader block547203 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000201F8D5E668DAA58B04FE17C1AA448FA143E64F01D9177030200000000000000001030F6AF6ED57728388DDBC309B0D690E70AA60E9EECE501AF2B50C3A1DE5594D6FD955B231D02181541C84F"));

        // IMPORTANT: This forces 01 and 03 to have the same timestamp, which then changes the selected block from 02 to 01.
        block547203.setTimestamp(block547201.getTimestamp());

        // 547201
        chainWork = ChainWork.add(chainWork, block547201.getDifficulty().calculateWork());
        databaseManager.registerBlockHeader(block547201, 547201L, chainWork);

        // 547202
        chainWork = ChainWork.add(chainWork, block547202.getDifficulty().calculateWork());
        databaseManager.registerBlockHeader(block547202, 547202L, null); // ChainWork is null in order to ensure the difficulty calculation fails if this block is selected...

        // 547203
        chainWork = ChainWork.add(chainWork, block547203.getDifficulty().calculateWork());
        databaseManager.registerBlockHeader(block547203, 547203L, null); // ChainWork is null in order to ensure the difficulty calculation fails if this block is selected...

        // 547204 (Block To Test...)
        final Long blockHeight = 547204L;
        final BlockHeader blockHeader = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000209C40E748F5BADA0670EE1F67EE0E3D9F48CE1D0753538E01000000000000000016C1FA95946D658E7F79DF9420EAF862F320A95630217EDED426114C46C1F5F88B01965B221D02186548ACB8"));
        databaseManager.registerBlockHeader(blockHeader, blockHeight, chainWork);

        // Action
        final Difficulty difficulty = difficultyCalculator.calculateRequiredDifficulty(blockHeader);

        // Assert
        Assert.assertNotNull(difficulty); // Since the difficulty no longer correlates to main net, success is defined by not receiving a null pointer exception during calculation...
    }

    @Test
    public void should_calculate_difficulty_for_block_000000000000000001B66EA0B6BC50561AC78F2168B2D57D3F565F902E0B958D() {
        // Setup
        final FakeDatabaseManager databaseManager = new FakeDatabaseManager();

        final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(databaseManager);
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000200E8030580910AC7F8B9CD43D7E47954BC674719076556D01000000000000000026DAF4F1E4EDA3C462486AB3FAFB41524B967D980E0B016E5C3154B690C744FD25A4BF5959070A18D9252ACB"));
            databaseManager.registerBlockHeader(blockHeader, 487181L, null);
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000203D6A33B85D679440694FDAB9BD8C633E092C0482AAB11F050000000000000000D005F4D450FA8044C4EB4934CF97700F27B9078745B7AF38BD750E8F2B4BD4A537A4BF5959070A1860C73BB6"));
            databaseManager.registerBlockHeader(blockHeader, 487182L, null);
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00000020B20BC05C3C7CB920D16B8B8FF2E825DE6AF1DD428629DE090000000000000000C2FBCB6FF985D915DA6ACD876A2257C18201AC08BA18D055FC7D0233399231EA6BA4BF5959070A18B5E1E659"));
            databaseManager.registerBlockHeader(blockHeader, 487183L, null);
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00000020AB6D9117993419E9B6EBBD8D0FDDE47EF55B7F7F606255070000000000000000DDFE36AA8F8DE82AE6A7A6A8AAFC2351129CC874E32251E468E1F0EC17290A9751A5BF5959070A185CE40B05"));
            databaseManager.registerBlockHeader(blockHeader, 487184L, null);
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000207C160E3CF3A7F7DE6E5E3934F16C345D293293A52F65F90600000000000000006BC7A3199E3B9A67FDEA6F3DD7B8F2AAF22C37746569B2F01FD0FA6DD181D2CE79A5BF5959070A185CFCF8D9"));
            databaseManager.registerBlockHeader(blockHeader, 487185L, null);
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00000020970930AB85C2D6047615D255AADF04AE5E4602F1482C69060000000000000000106241BEA7E98AA7BA92131BCBDC59071D6D21370CA8A5377E5B2E8156C172E656A6BF5959070A18F7ED0654"));
            databaseManager.registerBlockHeader(blockHeader, 487186L, null);
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000203AD535A1EE5F933BEE779600837C9BB705EEB3483C326C0900000000000000009BDFC42FD38D7D1AC4A2D3B308C68E567455F0457B56833A090FC3A27233F10341A6BF5959070A183209ADD2"));
            databaseManager.registerBlockHeader(blockHeader, 487187L, null);
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00000020B8C2A9FD684B59FF1E6A403CEE2597B2E2C01BC0A6F16F090000000000000000833F2E568FACD956043A3D7D0DCC44E1D47D903202FF0000315069F5BBD6FCE65AA6BF5959070A18B8C6561B"));
            databaseManager.registerBlockHeader(blockHeader, 487188L, null);
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000201499AD859A9F20738047C583EE3D70AA9B0A43C5E2787C0400000000000000001259E53B36979E9EF3E82E312C24D6830BF0BC7108CCC3279138D8D62A2B15AEFEA6BF5959070A1868C159D1"));
            databaseManager.registerBlockHeader(blockHeader, 487189L, null);
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00000020667C5C701D4CD746C65CE3E0A0B72F88EE0E89C077285C05000000000000000042FD07BBA2B423600F96A31F9B137792436D338B750BEB7A73ECB7B1A107D6DBC7A7BF5959070A189E1F64AC"));
            databaseManager.registerBlockHeader(blockHeader, 487190L, null);
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000204C6DA86DE6D3609D667AEEF26B0CAE2E08D28FAC22104700000000000000000050C4BCE1EE2559E703302002C9A978E3293D94D06717955FFF5D44AA1D670F9341A9BF5959070A180AB22D0D"));
            databaseManager.registerBlockHeader(blockHeader, 487191L, null);
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("0000002010CA0369BDB392B00E48A26E7297328D76F194B77F1064020000000000000000D99D1534FD63E87E0D5612BE3C3CBE51C923346E3053A923CBDE416556EDEEFB2D6C055D34510318CF7CCBB1"));
            databaseManager.registerBlockHeader(blockHeader, 587187L, null);
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00000020DFF37A1C662C4A3276C0653147A7B12417770F676DECDE000000000000000000E9A3D84E2660F026196820A2CDD80F1D95BF085E031D530ED1F1FEA2EF782EA6EB6C055DBC51031879511BEB"));
            databaseManager.registerBlockHeader(blockHeader, 587188L, null);
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("0000C020848A1F1662D218B9C061AB41595CD0B161E046B3C69B2C0300000000000000003361EC7AB0B32EC70659A63DA6E1F8CA944EA219E13745C3E422A2D47BFA0568FE6D055D93520318C0F46A2D"));
            databaseManager.registerBlockHeader(blockHeader, 587189L, null);
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00000020C1467E46D4E781856B255F844167D7AD41985F14038BF902000000000000000016019649B5EB3A7AF8EAD6BCFA1706DAE53C98111B15A567E4046163713C7128306E055DB6520318114AC70E"));
            databaseManager.registerBlockHeader(blockHeader, 587190L, null);
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("0000C020993AFB91A3DA06E8588ED7A2BEDF7EF580BD59139490EA000000000000000000228F3BECC2005B8252A843FA7614AB5886D848706CDBB744DF74F2636A0492F2936E055D71520318041D98D1"));
            databaseManager.registerBlockHeader(blockHeader, 587191L, null);
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00E00020CA3DD2220A79603CC01B5020AC742814696D6A5FDF5D2A0200000000000000003B169B33A91D61AE4EBA1E792FB1C6B8776626E6F398838A12BA991AE45AEFACD66E055D604703182F9A6631"));
            databaseManager.registerBlockHeader(blockHeader, 587192L, null);
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000209BAA890C096E7D25533EB6D83DE19F0AB48DF9214216CB080000000000000000D6352EAA8E8D8EABBBFD7006A5AB1916350981AD3158805C3BA91189020BAA688FA9BF5959070A1822984846"));
            databaseManager.registerBlockHeader(blockHeader, 487192L, null);
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("0000002037C3455AA34BA4B325339A38AD985FA773E3CAE4CD052A01000000000000000049F464F13D452661A86566BE978A8C1C07E263E57F29B5C48E965765EE93246D8B6F055D9C460318913BF6B5"));
            databaseManager.registerBlockHeader(blockHeader, 487193L, null);
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("0000FF3F4D4411F9E752E66CE1456860F57B9067870675910AC6F7000000000000000000B141E85299981AA63E6B6E4FAE213FA868B51493F2BFB89A558616FBF926B7D8A76F055D6E450318D6F88D95"));
            databaseManager.registerBlockHeader(blockHeader, 587194L, null);
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("0000FF3F396F0BFE2648C252F2E83DA61DB3164E228E9A08699ECC000000000000000000B5F208E22E350C16DA7AF7015FD375ED4CF86DF6833399F46767AE1A5A075B452370055D0B45031889818CB6"));
            databaseManager.registerBlockHeader(blockHeader, 587195L, null);
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("0000802029442002438B76394DB51AD4DFEBE6034FF760CC102B21020000000000000000DB02CFEED980E0B1DE1FBC2F32467C58948162931A119F99384BB8F980B6FC911870055D73440318CC25AE45"));
            databaseManager.registerBlockHeader(blockHeader, 487196L, null);
        }

        {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("000000204EC6D6C4A07F0D351A48DAA35E684B2ADC806D3F4C0397000000000000000000C67013C6B366AF5A19A8DB92187427384A097950E9E21B0F4BA3A3E271955ACF2370055DEA3D03188963EFB2"));
            databaseManager.registerBlockHeader(blockHeader, 487197L, null);
        }

        final BlockHeader blockHeader;
        { // Height: 587198
            blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00E0FF3FEA3DA6788DDDFB4BC584A6F342CE5AB6C4C67DDB4319860100000000000000003FB1703177952BCFA51D4AD1A099FE56D5E681266BDF4DAE12941DE9AC685AF3F871055D144703183F87E206"));
            databaseManager.registerBlockHeader(blockHeader, 487198L, null);
        }

        // Action
        final Difficulty difficulty = difficultyCalculator.calculateRequiredDifficulty(blockHeader);

        // Assert
        Assert.assertEquals(blockHeader.getDifficulty(), difficulty);
    }
}
