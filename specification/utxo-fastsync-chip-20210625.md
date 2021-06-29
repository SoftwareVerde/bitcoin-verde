```
Title: UTXO Fastsync
Created: 2021-06-25  
Last Edited: 2021-06-28
Type: Technical
Layer: Peer Services
Status: Draft  
Version: 0.0.1
Owner: Josh Green
```

##Summary

### Introduction

This proposal defines a method for nodes to generate, distribute, validate, and consume UTXO snapshots via the BCH P2P network.
These snapshots allow for new peers to synchronize to the current state of the network without processing the entire blockchain block-by-block.

This method of synchronizing allows new nodes to join the network within a couple of hours (or less), instead of the current average of 8+ hours.
Furthermore, this method of synchronizing (alongside block pruning methods) allows for BCH to scale in the medium-to-long term as the blockchain grows from gigabytes to terabytes.

This proposal mentions UTXO "commitments", but it is **not** recommending that miners commit to UTXO sets at this time.
Instead, this proposal attempts to outline a use case for UTXO snapshots, which paves the way to UTXO commitments in the future if desired.

### Technical Summary

Nodes generate UTXO snapshots every 10,000 blocks and advertise available snapshots to peers via the BCH P2P protocol.
Nodes may skip the initial block download by downloading the UTXO snapshot from peers;
snapshots are validated after downloading by checking their EC multiset hash (or rather, its public key) against the value hard-coded into the node software.
The UTXO snapshots are fragmented, similar to torrents, to allow multiple concurrent downloads and to facilitate resuming failed downloads.
In the future, snapshots may be validated by checking its hash (public key) committed into blocks by miners, removing 3rd-party trust.

## Discussion

Unspent Transaction Outputs (UTXOs) are (possibly) spendable coins within the BCH blockchain. 
The set of available UTXOs is the effective state of the BCH blockchain.
Each block is a set of modifications to the UTXO set, and a node is "synced" when all available (and valid) blocks having the most proof of work have been applied to a node's UTXO set.

The UTXO set is canonical with regard to its contents (assuming existing well-defined rules regarding what is "provably unspendable"), and any permutation of the set's contents between nodes would result in an eventual network fork;
therefore, all "in sync" nodes derive the exact same UTXO set.

Since the UTXO set's contents are canonical, any unsynchronized node may download the precalculated state from the network and have the same UTXO set as if the node had generated it itself.
This is sometimes referred to as "fast-syncing".

However, fast-syncing nodes must be able to validate that the UTXO set they recieved is in consensus with the rest of the network, otherwise malicious nodes could serve an invalid or incomplete UTXO set.
Currently, the BCH network does not provide a mechanism for nodes to validate a UTXO set, so it is currently not possible to fast-sync without relying on some amount of trust.
If UTXO commitments are adopted by miners in the future, then fast-syncing will become possible without relying on trust.

In addition to providing a P2P method for downloading UTXO snapshots, this proposal aims to define a "reduced trust" model to fast-syncing UTXO sets for use in the short term until UTXO commitments are adopted.
This sequence is proposed in hopes that its adoption can then justify a change to consensus such that zero trust is required.

| Term | Description |
| --- | --- |
| Block Pruning | Nodes download the entire blockchain but only store the blockchain's UTXO set and a small subset of the blockchain's blocks; old blocks are discarded and cannot be served to peers. |
| Fast Syncing | Nodes only download a snapshot of the UTXO set and a small subset of the blockchain's blocks.  Fast syncing nodes are also usually pruning nodes.  The UTXO set is assumed to be valid by relying on a 3rd party. |
| UTXO Commitment | A UTXO set's hash is put into each block's coinbase transaction when mined.  This hash is validated by peers when a block is accepted by the network, therefore an invalid UTXO commitment renders the block invalid. | 

## Motivation and Benefits

Syncing a BCH node via the traditional methodology, at the time of this writing, requires each node download over 180GB of data and process it in order to build the UTXO set.
Depending on a node's hardware and software, syncing the blockchain can take 8+ hours, and less optimized implementations can require upwards of 18+ hours to sync.

With usage and age, the blockchain grows in size.
The increased size also increases synchronization times.
As the Bitcoin Cash blockchain grows from gigabytes to terabytes, the required disk space and initial synchronization time may deter adoption from users and small businesses running their own node.
Additionally, this increase in hardware/time requirements creates an additional barrier of entry for new developers and new applications that require a node.

The time it takes to initially sync the blockchain also plays a factor in determining a responsible block size.
Once synchronized, some of the work to validate a block is distributed across the period it took to mine the block (~10 minutes).
However, during initial synchronization ("initial block download", IBD), all work needed to validate a block is performed up-front.
If block validation times during IBD average 1 minute per block (a real possibility for large blocks), then traditional synchronization time becomes 1/10th of the total age of the blockchain.
For example, if BCH had generated large (and full) blocks since its creation in 2009, and each of which took 1 minute to validate, then syncing a node without fast-syncing would require just under 1.3 years.
Therefore, fast-syncing and UTXO commitments are essential to the long-term sustainability of Bitcoin Cash.

## Description

### UTXO Commitment Format

NOTE: This proposal is a modified version of the work performed by [Tomas van der Wansem](https://github.com/tomasvdw/bips/blob/master/ecmh-utxo-commitment-0.mediawiki), as well as BCHD's implementation written by Chris Pacia.

The UTXO set for block N, is the set of all transaction outputs that are unspent **before**** the processing of
block N, and for which

* The locking script of the output is at most 10,000 bytes (inclusive).
* The first byte of the locking script is not 0x6A (aka, OP_RETURN).

Note that these criteria are included to prevent storing unspendable outputs, and match the current behavior of all known implementations.

** To clarify, the UTXO set for block N does not include the transactions within block N.
Therefore, the coinbase transaction's outputs of block N are not included in the UTXO set for block N.

The serialized UTXO set is the set of byte sequences constructed by serializing each output of the UTXO set as:

| Byte Count | Name | Description | Endian |
| --- | --- | --- | --- |
| 32 | transactionHash | The hash ("txid") of the transaction. | Little |
| 4 | outputIndex | The index of the output within the transaction. | Little |
| 4 | height, isCoinbase | The most significant bit is set if the UTXO is a coinbase output.  The remaining 31 bits** represent the block height. | Little |
| 8 | value | The amount, in satoshis. | Little |
| 1-4 | lockingScriptByteCount | The number of bytes in the locking script (as a compact variable length integer**). | Little |
| ? | lockingScript | The locking script ("scriptPubKey", "pk_script"). | Big |

** The Van der Wansem proposal was ambiguous in regard to its definition of the locking script byte count and UTXO height.
This proposal uses a 4-byte LE integer for the UTXO height/isCoinbase flag, and a 1 byte (up to 4) variable compact-length integer for its locking-script byte-count prefix.
Considering there are over 55 million UTXOs in the current set, many of which could use 1 byte instead of 4 bytes to encode the lockingScript byte count, converting this field to a compact variable length integer reduces the current UTXO snapshot size by ~160 MB.
This reduction is not unremarkable and poses little complexity to the format.

DISCUSSION: the above reasoning regarding choosing a compact variable length integer format vs 4-byte integer format for the lockingScriptByteCount could apply to outputIndex.
However, there is a well-established convention for outputIndex as being defined as a 4-byte integer.
The community should discuss if going against current convention and redefining the outputIndex for UTXO commitments is worth the 160+ MB gain in space-efficiency.

### EC Multiset: Public Key vs Hash

A UTXO commitment/snapshot is identified by its EC Multiset's public key; this is distinct from Van der Wansem's proposal.
Public keys were chosen to identify the EC multiset over the multiset's hash because the hash by itself cannot be used to (re)load the state of the EC multiset.
Using the public key directly instead of its hash enables mining nodes to use that commitment as a starting point when calculating the next utxo commitment;
otherwise, the entire utxo set would need to be processed up to that point in order to derive (and validate) its hash.
This is a particularly important property if/when downloading the entire blockchain is no longer commonplace and/or possible.
This concept can be extended to other applications, and possibly native Bitcoin scripts.
The difference in byte count between the set's hash and its (compressed) public key is merely one byte.

### Validation

UTXO commitment hashes are calculated by the contents of the set, irrespective of order.
Furthermore, adding and removing UTXOs from the set must be efficient, and since the UTXO set is unbounded in size, these operations must scale well with an unbounded element count.
To accomplish this, an EC Multiset is used.

EC Multisets have the following desired properties:
- items may be added and removed from an EC Multiset in constant time (O(1))
- items within the set may be added/removed irrespective of order

It should also be noted that EC Multisets have the following not-necessarily desired properties:
- items cannot be retrieved from the set
  - (the set contains the hash of items, not the items themselves)
- cannot quickly determine if an arbitrary item exists (or not) within the set
  - an item is provably in the set if the set's public key is at infinity after every item is removed (including the item itself)
- the same item may appear in the set multiple times
  - (important to call out that duplicate UTXOs should not be added to the set, as they are not traditionally considered spendable)

A brief overview of how an EC Multiset functions is as follows:

- the set is a point on the Secp256k1 curve
- an empty set starts at infinity
- when adding an item, the item is hashed
  - its hash is converted into a public key point
  - the point is then added to the set's current point
- when removing an item, the item is hashed
  - its hash is converted into a public key point
  - the point is then subtracted from the set's current point
- when deriving the set's hash, the set's point is hashed

For a detailed specification of the Secp256k1 EC Multiset used in this proposal, reference [this document](https://github.com/tomasvdw/bips/blob/master/ecmh.mediawiki) written by Van der Wansem.

When serializing an EC Multiset's public key, the compressed format should always be used.
Therefore an EC Multiset's public key is always 33 bytes.
If the set is not empty then its first byte (big endian) is always either `0x02` or `0x03`;
an empty set always has an empty public key (i.e., a key consisting of 33 bytes of `0x00`).

EC Multiset hashes share similar properties of additive mathematics.
These properties enable multithreaded processing of any multiset hash during both generation and validation.
This performance is considerably important when architecting a commitment schema that is generated by mining nodes.
Deriving the UTXO commitment hash must not induce a significant lag to block template (or coinbase) generation, nor should it create validation barriers or incentivize selfish mining.

### Generation

Unlike a merkle tree, deriving an EC Multiset's hash of a block's UTXO can be performed in O(n) complexity, where n is the number of UTXOs added/removed from the set, given that the previous block's EC Multiset's public key is at hand (not its hash).
Furthermore, calculating the next block's UTXO commitment hash is not influenced by the UTXO set's previous size; this property is important due to the unbounded nature of the UTXO set in the long-term.

Therefore, calculating the next block's UTXO commitment public key becomes:
1. take the current UTXO set's EC multiset public key
2. add each new output within the block to the multiset
3. remove each spent output within the block from the multiset
4. take the resulting public key

Nodes may create UTXO commitment/snapshots at an interval of their discretion.
Furthermore, determining when to purge old UTXO commitment/snapshots is up to the node's discretion.
Recommended conventions for generating and serving commitments is at every 10,000 blocks (that is, when `blockHeight % 10000 == 0`).
Recommended purge frequency is to keep at most 2 snapshots available at any time.

Unlike calculating the the UTXO commitment public key for block template generation or validation, generating a UTXO snapshot is not a priority for nodes.
Nodes provide UTXO snapshots at the convenience of other nodes, similar to serving historic blocks.
Therefore, it is recommended that nodes generate UTXO snapshots as a low-priority background process as to not interfere with or delay block validation/propagation.
It is recommended that snapshots are generated once the risk of a blockchain reorg is impossible or very unlikely for the snapshot-block. 

### P2P Distribution

The summarized workflow for a node syncing via fast-sync is as follows:

1. prefer/connect to peers advertising the utxo snapshot p2p service flag
2. request available utxo commitments from peers (which may return multiple snapshot "breakdown"s)
3. validate at least one peer has an acceptable snapshot available and that the breakdown matches the desired EC multiset public key (hard-coded into the node software)
4. download the utxo snapshot from 1 (or more) peers, validating that each bucket's contents match the snapshot bucket public key
5. load each utxo bucket into the node's utxo set
6. process remaining blocks normally

The more in depth workflow is as follows:

Since UTXO sets are not (yet) committed to by miners, unsynchronized nodes must rely on UTXO commitment parameters being hardcoded into the software, presumably updated on each release.

UTXO commitment advertisements include the set's public key, the block hash and height for its associated block, and the total number of bytes in the commit.
These extra fields help the syncing nodes identify peers attempting to serve invalid UTXO advertisements, and prevents peers from serving maliciously large buckets filled with incorrect data.
Syncing nodes can verify that the buckets sum to the correct commitment public key, and that the bucket sizes sum to the correct overall commitment size.
Furthermore, syncing nodes may decide their own policy regarding maximum bucket size with sub-buckets, which allows more efficient failover for failed downloads.

Instead of downloading and serving the entire UTXO commitment snapshot as a single multi-gigabyte-sized file, each commitment is distributed into multiple buckets.
The generation of these buckets is canonical and parallelizable.
Each bucket's EC multiset public key is defined by its contents.
The sum of the bucket's EC multiset public key sums to the overall commitment's public key.
The rules specifying the buckets is convention only, and is not enforced by mining nodes.
Peers with incompatible bucket definitions ignore the commitment breakdown after recognizing its incompatibility.
The current convention recommends 128 buckets.
Each UTXO is placed in its canonical bucket by taking the first 7 bits of the first byte of a single Sha256 hash of the following preimage:
1. the commitment's block hash, as little endian
2. the UTXO's transaction hash, as little endian
3. the UTXO's output index, as a 4-byte integer, little endian

The commitment's block hash is included in the bucket-index calculation in order to mitigate malicious crafting of outputs that could render buckets disproportionately sized.

If buckets surpass the node's configuration for the maximum bucket size (in bytes), then the node may further break up the bucket into sub-buckets.
The boundaries that these sub-buckets are delimited is up to the node, and therefore while downloading-nodes may download different buckets of the same commitment from various peers, all sub-buckets for a single bucket must be downloaded from the same peer and then concatenated together.
It is recommended that the delimitation of sub-buckets is at whole-UTXO boundaries, although not necessarily mandatory.
Furthermore, it is recommended that each bucket be in sorted-order by UTXO identifier, big endian.
Performance for importing sorted UTXOs vs non-sorted UTXOs can be significant; nodes benefiting from a sorted bucket should gracefully handle unsorted buckets.

The process of importing/consuming UTXO commitments is delegated to the node implementation.
It is recommended that nodes consuming a UTXO commitment also stores and advertises the commitment so that others may sync from it.

UTXO Sub-Bucket to Bucket reconstruction/validation:
```
 -----------------       -----------------               ------------- 
| utxo sub-bucket |     | utxo sub-bucket |             | utxo bucket |
|   public key    |  +  |   public key    |  +  ...  =  | public key  |
 -----------------       -----------------               ------------- 
```

UTXO Bucket to Snapshot reconstruction/validation:
```
 -------------       -------------               --------------- 
| utxo bucket |     | utxo bucket |             | utxo snapshot |
| public key  |  +  | public key  |  +  ...  =  | public key    |
 -------------       -------------               --------------- 
```


## Specification

### UTXO Service Bit

Nodes supporting UTXO snapshots should set the UTXO Commitments bit in their version message's services bitfield.

The bit chosen to represent this service is the 13th (`0x0D`) bit.

### P2P Messages

#### Get UTXO Commitments ("getutxocmts")

Requests a list of available UTXOs Snapshots.
This message should invoke a "utxocmts" message as a response.

This message has no content.

#### UTXO Commitments ("utxocmts")

Provides a variable sized collection of UTXO snapshot metadata that the node has available for download.

<ins>Message Format</ins>

| Field Name | Byte Count | Format | Description |
| --- | --- | --- | --- |
| version | 1-4 | compact variable length integer | The message version format (current format version is `1`). |
| snapshot count | 1-4 | compact variable length integer | The number of snapshots metadata contained within this message. |
| snapshots | ? | <ins>Snapshot Metadata</ins> | A variable number of snapshot metadata. |

<ins>Message Format - Snapshot Metadata</ins>

| Field Name | Byte Count | Format | Description |
| --- | --- | --- | --- |
| block hash | 32 | sha256 hash, little endian | The block hash associated with this UTXO snapshot. |
| block height | 4 | integer, little endian | The block height associated with this UTXO snapshot. |
| public key | 33 | public key, big endian | The secp256k1 point of the EC multiset for this UTXO snapshot. |
| byte count | 8 | integer, little endian | The total number of bytes within the utxo snapshot. |
| utxo bucket (x128) | ? | <ins>Snapshot Bucket Metadata</ins> | The bucket breakdown for this snapshot.  There are always 128 buckets per snapshot. |

<ins>Message Format - Snapshot Bucket Metadata</ins>

| Field Name | Byte Count | Format | Description |
| --- | --- | --- | --- |
| public key | 33 | public key, big endian | The secp256k1 point of the EC multiset for this UTXO snapshot bucket. |
| byte count | 8 | integer, little endian | The number of bytes within this UTXO snapshot bucket.  The sum of these must equal the byte count of the snapshot. |
| sub-bucket count | 1-4 | compact variable length integer | The number of sub-buckets this UTXO snapshot bucket has been broken up into.  Nodes may define their own policy for maximum bucket size; the recommended value is 32 MiB. |
| sub-buckets | ? | <ins>Snapshot SubBucket Metadata</ins> | The sub-bucket breakdown for this snapshot bucket. |

<ins>Message Format - Snapshot SubBucket Metadata</ins>

| Field Name | Byte Count | Format | Description |
| --- | --- | --- | --- |
| public key | 33 | public key, big endian | The secp256k1 point of the EC multiset for this UTXO snapshot sub-bucket. |
| byte count | 8 | integer, little endian | The number of bytes within this UTXO snapshot sub-bucket.  The sum of these must equal the byte count of the snapshot bucket. |

#### Get UTXO Snapshot Data ("getdata")

UTXO snapshot buckets and sub-buckets are requested via the traditional "getdata" P2P message.
This message is extended to include the UTXO snapshot data types.
Since the "getdata" message only supports 32 byte inventory hashes and the EC multiset public keys are 33 bytes, two inventory types are added to represent keys starting with `0x02` and `0x03`.

Inventory item type `0x434D5402` ("CMT"-02) is a "getdata" request for a snapshot bucket or sub-bucket whose public key is `0x02` concatenated with the inventory item type 32-byte payload.

Inventory item type `0x434D5403` ("CMT"-03) is a "getdata" request for a snapshot bucket or sub-bucket whose public key is `0x03` concatenated with the inventory item type 32-byte payload.

#### UTXO Snapshot Data ("utxocmt")

| Field Name | Byte Count | Format | Description |
| --- | --- | --- | --- |
| public key | 33 | public key, big endian | The secp256k1 point of the EC multiset for this UTXO snapshot bucket/sub-bucket. |
| byte count | 8 | integer, little endian | The number of bytes within this UTXO snapshot bucket/sub-bucket.  The byte count must match the advertised byte count within the UTXO Commitments ("utxocmts") message. |
| data | ? | bytes | The data for the bucket/sub-bucket.  The EC multiset public key must match this contents. |

The data field is 1 or more serialized UTXOs.
The EC multiset public key of the data must match the advertised public key.

### Duplicate UTXOs

Implementations **must not** include any duplicate UTXOs unless the first transaction's UTXOs have all been spent.
Therefore, if two duplicate UTXOs are found, the block height used during the calculation of the UTXO commitment hash must be the lower block height of the two. 
This rule is currently enforced by all nodes;
this rule is important to be replicated when generating UTXO snapshots, otherwise nodes may generate mismatching UTXO commitments due to UTXO block height being mismatched.

Blocks

`00000000000271A2DC26E7667F8419F2E15416DC6955E5A6C6CDF3F2574DD08E`
`00000000000743F190A18C5577A3C2D2A1F610AE9601AC046A38084CCB7CD721`

and

`00000000000AF0AED4792B1ACEE3D966AF36CF5DEF14935DB8DE83D6F9306F2F`
`00000000000A4D0A398161FFC163C503763B1F4360639393E0E4C8E300E0CAEC` 

create duplicate transactions and therefore duplicate UTXOs (with different block heights).

The correct UTXO block height for `E3BF3D07D4B0375638D5F1DB5255FE07BA2C4CB067CD81B84EE974B6585FB468:0` is `91722`.

The correct UTXO block height for `D5D27987D2A3DFC724E359870C6644B40E497BDC0589A033220FE15429D88599:0` is `91812`.

## Current Implementations

// TODO: Verde. BCHD.

## Implementation costs and risks

// TODO

## Ongoing costs and risks

// TODO

## Evaluation of Alternatives

### Super Progressive UTXO Transfer (Sprout)

**Benefits**

* (allegedly) does not need (historic) UTXO set snapshots
* could (potentially) be used to prove a UTXO exists at a particular block height

**Drawbacks**  

* Hierarchical schemas (i.e., merkle/merklix trees) do not scale well for a set that is technically unbounded.
Today, the UTXO set has roughly 55MM outputs;
even with log(n) processing that is problematic.
* This schema doesn't seem to account for validation of the commitment in real-time.
Recalculating the merkle root on a 55MM+ tree can be expensive and may further incentivize/enable selfish mining attacks.
* Incremental syncing seems problematic;
when syncing, as blocks come in the merkle tree structure changes, so requesting all branches of the utxo tree must be completed between a block being mined and a new block coming in.

### Van der Wansem Proposal / BCHD Implementation

This proposal is a derivative of Van der Wansem's proposal as well as BCHD's implementation, and for the most part this proposal is compatible.
There have been some minor bugs found during evaluation of BCHD's implementation during evaluation of this proposal but have been resolved prior this document's publishing.
Notable differences between Van der Wansem's proposal and this is the usage of the EC multiset's public key instead of its hash, which has been justified elsewhere in this document.
Other, less notable, changes include minor differences (and disambiguation) to the UTXO serialization format.

## Test Cases

// TODO: Generate node-implementation-agnostic test cases.

Edge-Cases:
* Duplicate UTXO scenario
* UTXO P2P messages with invalid byte-count breakdowns are considered invalid
* UTXO P2P messages with invalid public key breakdowns are considered invalid
* UTXO P2P messages with more/less than 128 buckets are considered invalid
* UTXO P2P messages containing invalid data (data not matching the advertised public key) are considered invalid
* UTXO P2P messages surpassing the configured max- bucket/sub-bucket size are not considered for download
* UTXO Snapshots for block N should not include block N's coinbase (or other generated outputs)
* Empty EC Multiset public key should be `000000000000000000000000000000000000000000000000000000000000000000` (32 bytes)
* UTXO P2P messages with even/odd public key mismatch
* Ensure consensus on block 690k UTXO set hash/public key.


## Security Considerations

The UTXO set is the set of spendable outputs and its integrity is necessary for a node to maintain consensus with the network.
A UTXO set missing an unspent output would consider a block or transaction spending that UTXO as invalid.

Any disruption to a node's UTXO set's integrity would cause the node to eventually lose consensus with the network.
Therefore, the worst disruption the BCH network could experience is a node syncing from a bad/corrupted UTXO snapshot;
this node would eventually get "stuck" at a block and the node admin would have to resync the node through the traditional method, but ultimately the rest of the network would be unaffected.

It is recommended that miners always mine from nodes that are synced traditionally.
Since miners are not creating UTXO commitments within their coinbase as a part of this proposal, and with the recommendation that miners do not fast-sync, **it is not possible for the network to fork due to the results of this proposal**.

In the unlikely (but technically possible) scenario that the majority of the network were running nodes synchronized via fast-sync, and the UTXO was corrupted or maliciously vouched for by developers, then it would be technically possible to censor transactions on the BCH network.
To mitigate this attack, node operators, developers, and members of the community should audit the UTXO sets hard-coded into the node software to the best of their ability.
This audit not only prevents an attack, but also hardens the network from an accidental fork in the future due to bugs in node implementations used for mining.

Exposing the hash (or public key) of a node's UTXO set in this phase protects the network from accidental or secret splits in the future, with or without UTXO commitments since inconsistencies between nodes and node implementations can be spotted concisely via a hash mismatch.

## List of Major Stakeholders

Participation in serving UTXO snapshots is voluntary. 
If UTXO commitments is enacted then serving snapshots would still remain optional, since computing the UTXO commitment public key does not require maintaining and serving a snapshot.

Similar to seeding on bittorrent, the decision to serve snapshots is left to both the node implementation maintainers and the users running full nodes.
Businesses running full nodes that have synced via fast-sync and have available upload-bandwidth can choose to serve UTXO snapshots they've generated as well as the snapshot they've synchronized from.

Those incentivized to run a node that serves UTXO snapshots should include anyone supporting this methodology of long-term scaling for BCH.

## Statements

This section has been intentionally left blank until statements have been acquired.

## License

To the extent possible under law, this work has waived all copyright and related or neighboring rights to this work under [CC0](https://creativecommons.org/publicdomain/zero/1.0/).