package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;

import java.util.Comparator;

/**
 * Selects a median BlockHeader based on its timestamp, as specified by:
 *  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/nov-13-hardfork-spec.md#footnotes
 *      A block is chosen via the following mechanism:
 *          Given a list: S = [B_n-2, B_n-1, B_n]
 *              a. If timestamp(S[0]) greater than timestamp(S[2]) then swap S[0] and S[2].
 *              b. If timestamp(S[0]) greater than timestamp(S[1]) then swap S[0] and S[1].
 *              c. If timestamp(S[1]) greater than timestamp(S[2]) then swap S[1] and S[2].
 *              d. Return S[1].
 */
class MedianBlockHeaderSelector {
    public static final Comparator<BlockHeader> stableSortBlockHeaderByTimestampDescending = new Comparator<BlockHeader>() {
        @Override
        public int compare(final BlockHeader blockHeader0, final BlockHeader blockHeader1) {
            final Long timestamp0 = blockHeader0.getTimestamp();
            final Long timestamp1 = blockHeader1.getTimestamp();
            return (timestamp1.compareTo(timestamp0));
        }
    };

    public static BlockHeader[] preOrderBlocks(final BlockHeader[] blockHeaders) {
        final BlockHeader[] copiedBlockHeaders = Util.copyArray(blockHeaders);

        final int requiredContinuousChainCount = (blockHeaders.length - 1);
        int swapCount = 0;
        int continuousChainCount = 0;
        int i = 0;
        Tuple<Integer, Integer> lastUnstableSwap = null;
        while (true) {
            if (swapCount > (blockHeaders.length * blockHeaders.length)) {
                Logger.debug("Unable to sort non-continuous Block Headers.");
                return null;
            }

            final BlockHeader blockHeader = copiedBlockHeaders[i];
            final BlockHeader nextBlockHeader = copiedBlockHeaders[i + 1];

            if (Util.areEqual(blockHeader.getHash(), nextBlockHeader.getPreviousBlockHash())) {
                continuousChainCount += 1;

                if (continuousChainCount >= requiredContinuousChainCount) { break; }
                i += 1;
            }
            else if (Util.areEqual(blockHeader.getPreviousBlockHash(), nextBlockHeader.getHash())) {
                MedianBlockHeaderSelector.swap(copiedBlockHeaders, i, (i + 1));
                lastUnstableSwap = null;
                swapCount += 1;
            }
            else { // The two adjacent blocks are not related, so swap the next out of the way...
                final int defaultDestabilizer = 2;
                final int destabilizer;
                { // Ensure the same swap is not continuously repeated...
                    final Tuple<Integer, Integer> thisUnstableSwap = new Tuple<Integer, Integer>((i + 1), (i + defaultDestabilizer));
                    if (Util.areEqual(thisUnstableSwap, lastUnstableSwap)) {
                        destabilizer = (thisUnstableSwap.second + 1);
                    }
                    else {
                        destabilizer = defaultDestabilizer;
                    }
                }
                lastUnstableSwap = new Tuple<Integer, Integer>((i + 1), (i + destabilizer));

                MedianBlockHeaderSelector.swap(copiedBlockHeaders, (i + 1), ((i + destabilizer) % blockHeaders.length));
                swapCount += 1;
                continuousChainCount = 0;
                i = 0;
            }
        }

        return copiedBlockHeaders;
    }

    public static void swap(final BlockHeader[] blockHeaders, final int index0, final int index1) {
        final BlockHeader swapSpace = blockHeaders[index0];
        blockHeaders[index0] = blockHeaders[index1];
        blockHeaders[index1] = swapSpace;
    }

    public BlockHeader selectMedianBlockHeader(final BlockHeader[] blockHeaders) {
        if (blockHeaders.length != 3) { return null; }

        // Given a list: S = [B_n-2, B_n-1, B_n]
        // NOTE: Despite being a sorting algorithm, the order of the original list matters.
        //  BlockHeaders must be in order of oldest-to-newest before executing the HF20171113 implementation.
        final BlockHeader[] copiedBlockHeaders = MedianBlockHeaderSelector.preOrderBlocks(blockHeaders);
        if (copiedBlockHeaders == null) { return null; }

        if (copiedBlockHeaders[0].getTimestamp() > copiedBlockHeaders[2].getTimestamp()) { // if (blocks[0]->nTime > blocks[2]->nTime) {
            MedianBlockHeaderSelector.swap(copiedBlockHeaders, 0, 2); // std::swap(blocks[0], blocks[2]);
        }

        if (copiedBlockHeaders[0].getTimestamp() > copiedBlockHeaders[1].getTimestamp()) { // if (blocks[0]->nTime > blocks[1]->nTime) {
            MedianBlockHeaderSelector.swap(copiedBlockHeaders, 0, 1); // std::swap(blocks[0], blocks[1]);
        }

        if (copiedBlockHeaders[1].getTimestamp() > copiedBlockHeaders[2].getTimestamp()) { // if (blocks[1]->nTime > blocks[2]->nTime) {
            MedianBlockHeaderSelector.swap(copiedBlockHeaders, 1, 2); // std::swap(blocks[1], blocks[2]);
        }

        return copiedBlockHeaders[1];
    }
}