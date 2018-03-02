package com.softwareverde.bitcoin.block.merkleroot;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;

/*

                     _ABCDEFGHIJKLMNOP_
                    /                  \
            ABCDEFGH                    IJKLMNOP
           /        \                  /        \
       ABCD          EFGH          IJKL          MNOP
      /    \        /    \        /    \        /    \
    AB      CD    EF      GH    IJ      KL    MN      OP
   /  \    /  \  /  \    /  \  /  \    /  \  /  \    /  \
  A    B  C   D E   F   G   H I    J  K   L M    N  O    P

 */

public interface MerkleTree {
    void addItem(final Hashable transaction);
    MerkleRoot getMerkleRoot();
}
