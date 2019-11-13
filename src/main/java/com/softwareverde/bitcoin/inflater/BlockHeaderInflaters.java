package com.softwareverde.bitcoin.inflater;

import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;

public interface BlockHeaderInflaters extends Inflater {
    BlockHeaderInflater getBlockHeaderInflater();
    BlockHeaderDeflater getBlockHeaderDeflater();
}
