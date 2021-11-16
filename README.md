# Bitcoin Verde v2.0.0

## Patch Notes

**v2.0.0**
- Non-Indexing Module
    - Reduced disk footprint to less than 300GB.
    - Reduced initial sync to less than 24hrs.
    - Reduced required CPU/Memory resources.
    - Resolves funded feature [#7][i7].
- 20201115 HF support (ASERT DAA).
    - Stabilizes block time intervals at 10minutes/block.
- CashAddr and SLP support for Explorer
    - Partly resolves funded feature [#10][i10].
- Improved logging performance and space-efficiency.
- Added historic checkpoints support to improve security during IBD.
- Fixed an issue preventing communication to Bitcoin Unlimited nodes.
- Added peer-discovery via DNS.

**NOTE**: v2 is fundamentally incompatible with v1.
If you are upgrading from v1, you must perform a full resynchronization from a clean directory.

[i7]: https://github.com/softwareverde/bitcoin-verde/issues/7
[i10]: https://github.com/softwareverde/bitcoin-verde/issues/10

**v1.4.0**
- Support for ASERT difficulty adjustment algorithm.

**v1.3.2**
- Fix for signature pre-image with OP_CODESEPARATOR.

**v1.3.1**
- Reverting difficulty calculation to an older, more stable version.
- Dependency updates and other minor improvements.

**v1.3.0**
- 20200515 HF support (OP_REVERSE).
- The Explorer now returns results via pagination for better performance.
- Added CashAddr and improved SLP support to the Explorer.
- Misc. Wallet Updates & Bug Fixes.
- Added Bitcoin-Sign-Message support.


**v1.2.0**
- 20191115 HF support (Multisig Schnorr Signatures).
- SLP Validation.
- Many SPV wallet SDK improvements.
- Additional RPC commands including SLP validation.
- Imported reference client test vectors to confirm compatibility.
- Implemented a block cache for facilitating other nodes during their initial block download and improving performance
of the explorer module.
- NUM2BIN now follows ABC's quirk of allowing large byte arrays for encoding.
- Migrated to Hikari database connection pool.
- Optional block header bootstrapping for improved initial block download.
- SPV nodes now receive matching mempool transactions when a bloom filter is set.

**v1.1.0**

- HF20190515 rules are now supported. (Schnorr Signatures)
- Headers are now bootstrapped during initial sync.
- SPV Bloom Filters are supported.
- Improved DOS defences against malicious nodes.
- Adding ASIC Mining module.
- Misc. Explorer improvements.


**v1.0.2**

- FIX: Subsequent embedded-db restarts no long fail to start. (Broken dependency)
- Added `BAN_NODE` and `UNBAN_NODE` RPC calls.
- RPC scripts are now copied to the out directory.

**v1.0.1**

- Added support for remote databases.
- Added RPC `ADD_HOOK` function.
- Explorer now lists new transactions and blocks on the home page.
- Updated documentation to include node message throttling configuration.
- Changed user-agent to use a space instead of hyphen.
- Implemented `getaddr` message type to facilitate node discovery.
- Fixed an issue causing TransactionBloomFilter to render significant false-positives due to integer overflow.

**v1.0.0** - Initial beta release.


## Description


Bitcoin-Verde is a ground-up implementation of the Bitcoin (Cash) (BCH) protocol.  This project is a
mining-enabled, all-in-one indexing full node, blockchain explorer, and library.


## Purpose


Bitcoin Core (BTC) is the primary group responsible for development on BTC's client.  In the past, lack of
a diversified development team and node implementation, have caused bugs to become a part of the protocol.
BCH currently has roughly multiple popular full-node implementations (BCHN, BCHD, Bitcoin Unlimited, Flowee,
and more).  However, many of these implementations are forked versions of Bitcoin Core, which means they
may share the same (undiscovered) bugs.  With a diverse network of nodes, bugs in the implementation of the
protocol will result in incompatible blocks, causing a temporary fork.  This situation is healthy for the
network in the long term, as the temporary forks will resolve over time, with the intended implementation
becoming the consensus.


## Disclaimer

Windows Users: Bitcoin Verde has been tested heavily on Linux and OS X.  On Windows, there may be many
issues.  If you are kind enough to run this implementation on Windows, please create an issue describing
the problem you encountered.

## Getting Started


To build the node, run the following:

```
./scripts/make.sh
```

This command will run the gradle command to download dependencies and build the jar file, its
configuration, and run-scripts, located within the `out` directory.

If you are running on OSX, you may need to run `./scripts/osx/link-openssl.sh` before running the node.

To run the node, you may `cd` into `out` and execute one of the `run-*` scripts. Or from the project
directory, run `./scripts/run-node.sh` (or `./run-node.sh` from the `out` directory).

Review the `out/conf/server.conf` and change the node's settings as desired.

The default configuration requires ~6GB ram at its peak, and a 300GB SSD to perform optimally.  Best performance is
achieved by increasing the `bitcoin.database.maxMemoryByteCount` and by running on an NVME drive.

For more detailed instructions, please refer to https://explorer.bitcoinverde.org/documentation/


## Upgrading to v2.0.0 from v1.+

Bitcoin Verde v2 is fundamentally incompatible with v1.
If you are upgrading from v1, you must perform a full resynchronization from a clean directory.

The `make.sh` script will completely remove the `out` directory, which is where blocks, indexes, and
configurations are stored.  Running this command to upgrade will cause your node to re-sync from scratch.
To upgrade, shutdown your node via `./scripts/shutdown.sh`, then remove (or make a backup of) your existing `out`
directory.  Checkout `v2.0.0` and run `./scripts/make.sh`.  Finally, start your node via `./scripts/rpc/run-node.sh`.


## Running the Node as a Service/Daemon


The ./scripts/run-node.sh script will run the node in the current shell, which will exit upon logout.
To run the Node as a detached process, you can start the run-node script via nohup, like so:
`nohup ./scripts/run-node.sh &`.

Logs are located within `out/logs/node.log`, and are gzipped and rotated.  You can monitor your node's progress via
the explorer or by tailing the logs directly via: `tail -F out/logs/node.log`.

Alternatively, you can install the daemon scripts located in `daemons` directory.  Currently,
Bitcoin Verde comes with init.d and systemd versions of these scripts.  Installing them is
dependent on your OS, and is out of scope of this README, but there are plenty of guides online
for installing systemd or init.d processes.


## Contributions


Any contributions are welcomed and will be reviewed via pull-requests.  In order to be accepted,
care must be taken for all immutable classes and their mutable counterparts.  Additionally, PR
resolving bugs preferrably be accepted along with a test proving their existence and fix.


## Contact


Feel free to contact Software Verde, LLC at any appropriate softwareverde.com email address.
Generic enquiries may be directed to bitcoin-verde@softwareverde.com


## Running on macOS Catalina

If the embedded database fails to start due to being unable to find the SSL lib, consider
executing the following commands and trying again: `./scripts/osx/link-openssl.sh`

