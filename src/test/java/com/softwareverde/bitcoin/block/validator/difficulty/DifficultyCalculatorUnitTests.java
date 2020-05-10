package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.header.MutableBlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.test.fake.database.FakeBlockHeaderDatabaseManager;
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
            public BlockchainSegmentId getBlockchainSegmentId(final BlockId blockId) {
                return BLOCKCHAIN_SEGMENT_ID;
            }

            @Override
            public BlockId getBlockHeaderId(final Sha256Hash blockHash) {
                return _blockIds.get(blockHash);
            }

            @Override
            public Long getBlockHeight(final BlockId blockId) {
                return _blockHeights.get(blockId);
            }

            @Override
            public BlockId getAncestorBlockId(final BlockId blockId, final Integer parentCount) {
                final Long blockHeight = _blockHeights.get(blockId);
                final Long requestedBlockHeight = (blockHeight - parentCount);
                return _blocksByBlockHeight.get(requestedBlockHeight);
            }

            @Override
            public BlockId getBlockIdAtHeight(final BlockchainSegmentId blockchainSegmentId, final Long blockHeight) {
                if (! Util.areEqual(blockchainSegmentId, BLOCKCHAIN_SEGMENT_ID)) { return null; }

                return _blocksByBlockHeight.get(blockHeight);
            }

            @Override
            public BlockHeader getBlockHeader(final BlockId blockId) {
                return _blockHeaders.get(blockId);
            }

            @Override
            public ChainWork getChainWork(final BlockId blockId) {
                return _chainWork.get(blockId);
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
}
