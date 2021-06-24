```
Title: UTXO Fastsync
Created: 2021-06-25  
Last Edited: 2021-06-25
Type: Technical
Layer: Peer Services
Status: Draft  
Version: 0.0.1
Owner: Josh Green
```

### Summary

This proposal defines a method for nodes to generate, distribute, validate, and consume UTXO snapshots via the BCH P2P network.
These snapshots allow for new peers to synchronize to the current state of the network without processing the entire blockchain block-by-block.

This method of synchronizing allows new nodes to join the network within a couple of hours (or less), instead of the current average of 8+ hours.
Furthermore, this method of synchronizing (alongside block pruning methods) allows for BCH to scale in the medium-to-long term as the blockchain grows from gigabytes to terabytes.

This proposal mentions UTXO "commitments", but it is **not** recommending that miners commit to UTXO sets at this time.
Instead, this proposal attempts to outline a use case for UTXO snapshots, which paves the way to UTXO commitments in the future if desired.

### Discussion

Unspent Transaction Outputs (UTXOs) are (possibly) spendable coins within the BCH blockchain. 
The set of available UTXOs is the effective state of the BCH blockchain.
Each block is a set of modifications to the UTXO set, and a node is "synced" when all available (and valid) blocks having the most proof of work have been applied to a node's UTXO set.

The UTXO set is canonical with regard to its contents, and any permutation of the set's contents between nodes would result in an eventual network fork;
therefore, all "in sync" nodes derive the exact same UTXO set (assuming existing well-defined rules regarding what is "provably unspendable").

Since the UTXO set's contents are canonical, any unsynchronized node may download the precalculated state from the network and have the same UTXO set as if the node had generated it itself.
This is often referred to as "fast-syncing".

However, fast-syncing nodes must be able to validate that the UTXO set they recieved is in consensus with the rest of the network, otherwise malicious nodes could serve an invalid or incomplete UTXO set.
Currently, the BCH network does not provide a mechanism for nodes to validate a UTXO set, so it is not possible download the UTXO set without relying on trust.

In addition to providing a P2P method for downloading UTXO snapshots, this proposal aims to define a "reduced trust" model to fast-syncing UTXO sets, in hopes that its adoption can later justify a change to consensus such that zero trust is required.

### Motivation and Benefits

Depending on a node's hardware and software, syncing the entire BCH blockchain (at the time of writing) takes 8+ hours of processing, and less optimized clients often require upwards of 18+ hours to sync.

As the blockchain grows in size, these synchronization times also grow.
Long synchronization times may deter adoption from small business running their own node, may increase barriers of entry for new developers and new applications requiring a node.
The time it takes to synchronize a node may also put an indirect limit on the maximum size of BCH blocks**.

** As a simplistic example, if blocks took longer than 10 minutes to process during the initial block download (IBD), then the node would never catch up to the network and the synchronization process would never complete.

### Description

#### UTXO Commitment Format

This proposal is derived from the work performed by [Tomas van der Wansem](https://github.com/tomasvdw/bips/blob/master/ecmh-utxo-commitment-0.mediawiki), as well as BCHD's implementation written by Chris Pacia.

The UTXO set for block N, is the set of all transaction outputs that are unspent **before**** the processing of
block N, and for which

* The locking script of the output is at most 10,000 bytes (inclusive).
* The first byte of the locking script is not 0x6A (aka, OP_RETURN).

Note that these criteria are included to prevent storing unspendable outputs, and match the current behaviour of all implementations.

** To clarify, the UTXO set for block N does not include transactions generated within block N.

The serialized UTXO set is the set of byte sequences constructed by serializing each output of the UTXO set as:

| Field Size | Name | Description |
| --- | --- | --- |
| 32 | transactionHash | The hash ("txid") of the transaction. |
| 4 | outputIndex | The index of the output within the transaction. |
| 4 | height, isCoinbase | The most significant bit is set if the UTXO is a coinbase output.  The remaining 31 bits represent the block height. |
| 8 | value | The amount, in satoshis. |
| 4 | lockingScriptByteCount | The number of bytes in the locking script. |
| ? | lockingScript | The locking script ("scriptPubKey", "pk_script"). |

#### Validation

UTXO commitment hashes are calculated by the contents of the set, irrespective of order.
To accomplish this, an EC Multiset is used.

EC Multisets have the following properties:
- items may be added and removed from an EC Multiset in constant time (O(1))
- items within the set may be added/removed irrespective of order
- items cannot be retrieved from the set
- the same item may appear in the set multiple times
- cannot quickly determine if an arbitrary item exists (or not) within the set**

** To determine if an individual item is in the set, every item (including itself) must be removed and asserting that the set's public key is equal to infinity.

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

EC Multiset hashes share similar properties of additive mathematics.
These properties enable multithreaded processing of any multiset hash during both generation and validation.
This performance is considerably important when architecting a commitment schema that is generated by mining nodes.
Deriving the UTXO commitment hash must not induce a significant lag to block template (or coinbase) generation, nor should it create validation barriers or incentivize selfish mining.

#### Generation

Unlike a merkle tree, deriving an EC Multiset's hash of a block's UTXO can be performed in O(n) complexity, where n is the number of UTXOs added/removed from the set, given that the previous block's EC Multiset is at hand (not only its hash).
Furthermore, calculating the next block's UTXO commitment hash is not influenced by the UTXO set size; this property is important due to the unbounded nature of the UTXO set in the long-term.

Therefore, calculating the next block's UTXO commitment becomes:
1. take the current utxo commitment ec multiset
2. add all each new output within the block to the multiset
3. remove each spent output within the block from the multiset

#### P2P Distribution

The summarized workflow for a node syncing via fast-sync is as follows:

1. prefer/connect to peers advertising the utxo commitment p2p service flag
2. request available utxo commitments from peers (which may return multiple commitment "breakdown"s)
3. validate at least one peer has an acceptable commitment available and that the breakdown matches the desired ec multiset hash
4. download the utxo commitment from 1 (or more) peers, validating that each bucket's contents match the commitment bucket hash
5. load each utxo bucket into the node's utxo set
6. process remaining blocks normally

The more in depth workflow is as follows:

Since UTXO sets are not (yet) committed to by miners, unsynchronized nodes must rely on UTXO commitment parameters being hardcoded into the software, presumably updated on each release.

A UTXO commitment is identified by its EC Multiset's public key; this is distinct from Van der Wansem's proposal.
Public keys were chosen to identify the EC Multiset over the Multiset's hash because the hash by itself cannot be used to (re)load the state of the EC Multiset.
Using the public key directly (instead of its hash) allows mining nodes to refer to a previous commitment to calculate the state of the utxo set at that block;
this implies a fresh mining node may generate valid utxo commitments if the previous block's commitment is correct and the application correctly applied the next block(s)'s UTXOs to the set.
This concept can be extended to other applications, and possibly native Bitcoin scripts.
Furthermore, the difference in byte count between the set's hash and its (compressed) public key is merely one byte.

UTXO commitment advertisements include its set's public key, the block hash and height for its associated block, and the total number of bytes in the commit.
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

Nodes may create UTXO commitment/snapshots at an interval of their discretion.
Furthermore, determining when to purge old UTXO commitment/snapshots is up to the node's discretion.
Recommended conventions for generating and serving commitments is at every 10,000 blocks (that is, when `blockHeight % 10000 == 0`).
Recommended purge frequency is to keep at most 2 snapshots available at any time.

### Specification

This section should contain detailed specifications of the proposal, including but not limited to protocol formats, parameter values and expected workflows. It can be updated as the CHIP makes its way through evaluation and testing, but the owner is expected to make a good-faith attempt at first publication.

### Current Implementations

This section should contain examples and proof-of-concepts of the CHIP's proposed change, or links to them. It can be updated as the CHIP makes its way through evaluation and testing.

### Implementation costs and risks

This section should list immediate costs and risks to the ecosystem if the proposal is adopted. This may include developer labor, adaptation of downstream software, risk of dissatisfaction leading to a major split, and other relevant concerns. Similar to "benefits", it is recommended that costs and risks be described in the context of their consequences to long term BCH value.

### Ongoing costs and risks

This section should list ongoing costs and risks to the ecosystem if the proposal is adopted, beyond immediate activation-time concerns. This may include node operator costs, software maintenance burden, loss of utility, loss of reputation, and so on.

### Evaluation of Alternatives

This section should describe significant alternatives to the proposal, and a brief comparison of each of their benefits/costs.

### Test Cases

This section should contain test cases that can be used to evaluate new implementations against existing ones. It can be updated as the CHIP makes its way through evaluation and testing.

### Security Considerations

This section should contain foreseeable security implications to the network. Common technical considerations include consensus risk, denial-of-service risk, and ongoing maintenance burden from additional complexity that can lead to rising likelihoods of either.

### List of Major Stakeholders

This section should list relevant stakeholder groups who needs to be consulted. The proposal owner should include a wide, persuasive selection from major actors in each of the groups in "Statements" below.

### Statements

This section collects comments from the above group of stakeholders, whether they are supportive, skeptical or otherwise providing relevant input. CHIP owners should be responsible for populating this section as far as possible to be persuasive to node developers and other stakeholders alike.

#### Full node developers

#### Major businesses

#### Miners and pools

#### Application developers

#### Service operators

### License

To the extent possible under law, this work has waived all copyright and related or neighboring rights to this work under [CC0](https://creativecommons.org/publicdomain/zero/1.0/).