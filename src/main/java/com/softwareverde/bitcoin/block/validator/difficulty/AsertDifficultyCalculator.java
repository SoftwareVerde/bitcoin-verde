package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.bip.HF20201115;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.util.Util;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

import java.math.BigInteger;

/**
 * BCH Difficulty Calculation via aserti3-2d algorithm.
 * The aserti3-2d difficulty adjustment algorithm (ASERT DAA) on Bitcoin Cash (BCH) on November 15th, 2020, as designed by Mark Lundeberg and implemented by Jonathan Toomim.
 *  https://gitlab.com/bitcoin-cash-node/bchn-sw/qa-assets/-/tree/master/test_vectors/aserti3-2d
 *  https://github.com/pokkst/bitcoincashj/blob/master/core/src/main/java/org/bitcoinj/params/AbstractBitcoinNetParams.java
 */
public class AsertDifficultyCalculator {
    public static final Long TARGET_BLOCK_SPACING = (10L * 60L); // 10 minutes per block.
    public static final Long HALF_LIFE = (2L * 24L * 60L * 60L); // 2 Days, in seconds.

    protected BigInteger _getHalfLife() {
        return BigInteger.valueOf(AsertDifficultyCalculator.HALF_LIFE);
    }

    protected Difficulty _computeAsertTarget(final AsertReferenceBlock referenceBlock, final Long previousBlockTimestamp, final BigInteger previousBlockHeight) {
        final int shiftBitCount = 16;

        final Long referenceBlockTime = referenceBlock.parentBlockTimestamp;
        final BigInteger heightDiff = previousBlockHeight.subtract(referenceBlock.blockHeight);
        final long blockTimeDifferenceInSeconds = (previousBlockTimestamp - referenceBlockTime);
        Logger.trace("anchor_bits=" + referenceBlock.difficulty.encode() + ", time_diff=" + blockTimeDifferenceInSeconds + ", height_diff=" + heightDiff);

        final BigInteger heightDifferenceWithOffset = heightDiff.add(BigInteger.ONE);
        final BigInteger desiredHeight = BigInteger.valueOf(TARGET_BLOCK_SPACING).multiply(heightDifferenceWithOffset);

        final int shiftCount;
        final BigInteger exponent;
        {
            final BigInteger halfLifeBigInteger = _getHalfLife();
            final BigInteger value = (((BigInteger.valueOf(blockTimeDifferenceInSeconds).subtract(desiredHeight)).shiftLeft(shiftBitCount)).divide(halfLifeBigInteger));
            shiftCount = (value.shiftRight(shiftBitCount)).intValue();
            exponent = value.subtract(BigInteger.valueOf(shiftCount << shiftBitCount));
        }

        final BigInteger target;
        {
            // factor = ((195766423245049 * exponent) + (971821376 * exponent^2) + (5127 * exponent^3) + 2^47) >> 48
            final BigInteger factor =
                BigInteger.valueOf(195766423245049L)
                .multiply(exponent)
                .add(BigInteger.valueOf(971821376L).multiply(exponent.pow(2)))
                .add(BigInteger.valueOf(5127L).multiply(exponent.pow(3)))
                .add(BigInteger.valueOf(2L).pow(47))
                .shiftRight(48);

            final BigInteger radix = BigInteger.ONE.shiftLeft(shiftBitCount); // 1 << shiftBitCount
            final BigInteger referenceBlockDifficulty = Difficulty.toBigInteger(referenceBlock.difficulty);
            final BigInteger unshiftedTargetDifficulty = referenceBlockDifficulty.multiply(radix.add(factor)); // referenceBlockDifficulty * (radix + factor)

            final BigInteger shiftedTargetDifficulty;
            if (shiftCount < 0) {
                shiftedTargetDifficulty = unshiftedTargetDifficulty.shiftRight(Math.abs(shiftCount));
            }
            else {
                shiftedTargetDifficulty = unshiftedTargetDifficulty.shiftLeft(shiftCount);
            }

            target = shiftedTargetDifficulty.shiftRight(shiftBitCount);
        }

        if (target.equals(BigInteger.ZERO)) {
            return Difficulty.fromBigInteger(BigInteger.ONE);
        }

        final BigInteger maxDifficulty = Difficulty.toBigInteger(Difficulty.MAX_DIFFICULTY);
        if (target.compareTo(maxDifficulty) > 0) {
            return Difficulty.MAX_DIFFICULTY;
        }

        return Difficulty.fromBigInteger(target);
    }

    public Difficulty computeAsertTarget(final AsertReferenceBlock referenceBlock, final Long previousBlockTimestamp, final Long blockHeight) {
        // For calculating the Difficulty of blockHeight N, the parent height, while technically an arbitrary choice, is used in due to the evolution of the algorithm.
        final BigInteger previousBlockHeightBigInteger = BigInteger.valueOf(blockHeight - 1L);
        return _computeAsertTarget(referenceBlock, previousBlockTimestamp, previousBlockHeightBigInteger);
    }

    public static class ReferenceBlockLoaderContextCore implements AsertReferenceBlockLoader.ReferenceBlockLoaderContext {
        protected final BlockchainDatabaseManager _blockchainDatabaseManager;
        protected final BlockHeaderDatabaseManager _blockHeaderDatabaseManager;

        public ReferenceBlockLoaderContextCore(final DatabaseManager databaseManager) {
            _blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            _blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
        }

        public BlockId getHeadBlockIdOfBlockchainSegment(final BlockchainSegmentId blockchainSegmentId) throws ContextException {
            try {
                return _blockchainDatabaseManager.getHeadBlockIdOfBlockchainSegment(blockchainSegmentId);
            }
            catch (final DatabaseException exception) {
                throw new ContextException(exception);
            }
        }

        public MedianBlockTime getMedianBlockTime(final BlockId blockId) throws ContextException {
            try {
                return _blockHeaderDatabaseManager.calculateMedianBlockTime(blockId);
            }
            catch (final DatabaseException exception) {
                throw new ContextException(exception);
            }
        }

        public Long getBlockTimestamp(final BlockId blockId) throws ContextException {
            try {
                return _blockHeaderDatabaseManager.getBlockTimestamp(blockId);
            }
            catch (final DatabaseException exception) {
                throw new ContextException(exception);
            }
        }

        public Long getBlockHeight(final BlockId blockId) throws ContextException {
            try {
                return _blockHeaderDatabaseManager.getBlockHeight(blockId);
            }
            catch (final DatabaseException exception) {
                throw new ContextException(exception);
            }
        }

        public BlockId getBlockIdAtHeight(final BlockchainSegmentId blockchainSegmentId, final Long blockHeight) throws ContextException {
            try {
                return _blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, blockHeight);
            }
            catch (final DatabaseException exception) {
                throw new ContextException(exception);
            }
        }

        public Difficulty getDifficulty(final BlockId blockId) throws ContextException {
            try {
                final BlockHeader blockHeader = _blockHeaderDatabaseManager.getBlockHeader(blockId);
                if (blockHeader == null) { return null; }

                return blockHeader.getDifficulty();
            }
            catch (final DatabaseException exception) {
                throw new ContextException(exception);
            }
        }
    }

    public static class AsertReferenceBlockLoader {
        public interface ReferenceBlockLoaderContext {
            BlockId getHeadBlockIdOfBlockchainSegment(BlockchainSegmentId blockchainSegmentId) throws ContextException;
            MedianBlockTime getMedianBlockTime(BlockId blockId) throws ContextException;
            Long getBlockTimestamp(BlockId blockId) throws ContextException;
            Long getBlockHeight(BlockId blockId) throws ContextException;
            BlockId getBlockIdAtHeight(BlockchainSegmentId blockchainSegmentId, Long blockHeight) throws ContextException;
            Difficulty getDifficulty(BlockId blockId) throws ContextException;
        }

        protected final ReferenceBlockLoaderContext _context;

        public AsertReferenceBlockLoader(final ReferenceBlockLoaderContext context) {
            _context = context;
        }

        // TODO: Hardcode value after 2020-11-15...
        public AsertReferenceBlock getAsertReferenceBlock(final BlockchainSegmentId blockchainSegmentId) throws ContextException {
            final BlockId headBlockId = _context.getHeadBlockIdOfBlockchainSegment(blockchainSegmentId);

            final MedianBlockTime headMedianBlockTime = _context.getMedianBlockTime(headBlockId);
            if (! HF20201115.isEnabled(headMedianBlockTime)) {
                Logger.debug("Cannot load Aserti3-2d anchor Block; HF has not activated.");
                return null;
            }

            final Long headBlockHeight = _context.getBlockHeight(headBlockId);

            Long maxBlockHeight = headBlockHeight;
            Long minBlockHeight = null;
            int scaleFactor = 2016;
            int parentCount = 144;
            while (true) {
                final BlockId blockId = _context.getBlockIdAtHeight(blockchainSegmentId, (headBlockHeight - parentCount)); // blockHeaderDatabaseManager.getAncestorBlockId(parentBlockId, parentCount);
                if (blockId == null) { break; }

                final MedianBlockTime medianBlockTime = _context.getMedianBlockTime(blockId);
                if (! HF20201115.isEnabled(medianBlockTime)) {
                    minBlockHeight = _context.getBlockHeight(blockId);
                    break;
                }
                else {
                    maxBlockHeight = _context.getBlockHeight(blockId);
                }

                parentCount += scaleFactor;
                scaleFactor *= 2;
            }

            if (minBlockHeight == null) {
                Logger.debug("No anchor Block found.");
                return null;
            }

            while (true) {
                final Long blockHeight = ((maxBlockHeight + minBlockHeight) / 2L);
                if (Util.areEqual(minBlockHeight, blockHeight)) { break; }

                final BlockId blockId = _context.getBlockIdAtHeight(blockchainSegmentId, blockHeight);
                if (blockId == null) {
                    Logger.debug("No anchor Block found.");
                    return null;
                }

                final MedianBlockTime medianBlockTime = _context.getMedianBlockTime(blockId);
                if (! HF20201115.isEnabled(medianBlockTime)) {
                    minBlockHeight = blockHeight;
                }
                else {
                    maxBlockHeight = blockHeight;
                }
            }

            final Long blockHeight = maxBlockHeight;
            final Long parentBlockHeight = ((blockHeight > 0L) ? (blockHeight - 1L) : 0L);
            final BlockId blockId = _context.getBlockIdAtHeight(blockchainSegmentId, blockHeight);
            final BlockId parentBlockId = _context.getBlockIdAtHeight(blockchainSegmentId, parentBlockHeight);

            final Difficulty difficulty = _context.getDifficulty(blockId);
            final Long previousBlockTimestamp = _context.getBlockTimestamp(parentBlockId);

            return new AsertReferenceBlock(blockHeight, previousBlockTimestamp, difficulty);
        }
    }

    public static class ContextException extends Exception {
        public ContextException(final String message) {
            super(message);
        }

        public ContextException(final Exception baseException) {
            super(baseException);
        }

        public ContextException(final String message, final Exception baseException) {
            super(message, baseException);
        }
    }
}
