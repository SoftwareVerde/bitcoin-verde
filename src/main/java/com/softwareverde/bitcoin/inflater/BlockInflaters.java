package com.softwareverde.bitcoin.inflater;

import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockInflater;

public interface BlockInflaters extends Inflater {
    BlockInflater getBlockInflater();
    BlockDeflater getBlockDeflater();
}
