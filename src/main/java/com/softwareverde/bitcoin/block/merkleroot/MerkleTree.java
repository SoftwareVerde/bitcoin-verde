package com.softwareverde.bitcoin.block.merkleroot;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;

/*
    // Correct Ordering:

    A

    AB
   /  \
  A    B

       ABCC
      /    \
    AB      CC
   /  \    /  \
  A    B  C  [ ]

       ABCD
      /    \
    AB      CD
   /  \    /  \
  A    B  C   D

            ABCDEE
           /      \
       ABCD        EE
      /    \      /  \
    AB      CD   E   [ ]
   /  \    /  \
  A    B  C   D

            ABCDEF
           /      \
       ABCD        EF
      /    \      /  \
    AB      CD   E    F
   /  \    /  \
  A    B  C   D

            ABCDEFGG
           /        \
       ABCD          EFGG
      /    \        /    \
    AB      CD    EF      GG
   /  \    /  \  /  \    /  \
  A    B  C   D E   F  G   [ ]

 */


/*
    // Incorrect Ordering:

            ABCDEFGG
           /        \
       ABEF          CDGG
      /    \        /    \
    AB      EF    CD      GG
   /  \    /  \  /  \    /  \
  A    B  E   F C   D  G   [ ]

            ABCDE
           /     \
        ABE       CD
       /   \     /  \
     AB     EE  C    D
    /  \   /  \
   A    B E  [ ]

 */

public interface MerkleTree {
    void addTransaction(final Transaction transaction);
    MerkleRoot getMerkleRoot();
}
