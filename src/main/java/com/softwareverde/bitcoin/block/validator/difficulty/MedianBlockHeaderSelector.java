package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.util.Util;

/**
 * Selects a median BlockHeader based on its timestamp, as specified by:
 *  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/nov-13-hardfork-spec.md#footnotes
 *      """A block is chosen via the following mechanism:
 *          Given a list: S = [B_n-2, B_n-1, B_n]
 *              a. If timestamp(S[0]) greater than timestamp(S[2]) then swap S[0] and S[2].
 *              b. If timestamp(S[0]) greater than timestamp(S[1]) then swap S[0] and S[1].
 *              c. If timestamp(S[1]) greater than timestamp(S[2]) then swap S[1] and S[2].
 *              d. Return S[1].
 *      """
 */
class MedianBlockHeaderSelector {
    public static void swap(final BlockHeader[] blockHeaders, final int index0, final int index1) {
        final BlockHeader swapSpace = blockHeaders[index0];
        blockHeaders[index0] = blockHeaders[index1];
        blockHeaders[index1] = swapSpace;
    }

    public BlockHeader selectMedianBlockHeader(final BlockHeader[] blockHeaders) {
        if (blockHeaders.length != 3) { return null; }

        final BlockHeader[] copiedBlockHeaders = Util.copyArray(blockHeaders);

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