package com.softwareverde.bitcoin.chain.utxo;

import com.softwareverde.bitcoin.transaction.output.UnspentTransactionOutput;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public interface CommittedUnspentTransactionOutput extends UnspentTransactionOutput {
    // The original specification for the UTXO set was contradictory/broken, and instructs that the LSB bit is
    //  set when flagging the UTXO's isCoinbase flag: https://github.com/tomasvdw/bips/blob/master/ecmh-utxo-commitment-0.mediawiki#specification
    //  This is contradictory to allowing 31 bits for the blockHeight.  Unfortunately, other implementations followed
    //  the specification (which only allows for 24 bits for the blockHeight).
    // Once coordinated, nodes will switch to the first bit for the coinbase flag.
    Integer IS_COINBASE_FLAG_BIT_INDEX = 7; // 0

    Boolean isCoinbaseTransaction();
    Sha256Hash getTransactionHash();
    Integer getByteCount();

    ByteArray getBytes();
}
