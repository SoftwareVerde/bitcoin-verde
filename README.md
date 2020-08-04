# Bitcoin Verde v1.3.1


## Updates

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
- Implemented a block cache for facilitating other nodes during their initial block download and improving performance of the explorer module.
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


Bitcoin-Verde is a ground-up implementation of the Bitcoin (Cash) (BCH) protocol.  This project is an
indexing full node, blockchain explorer, and library.


## Purpose


Bitcoin Core (BTC) is the primary group responsible for development on BTC's client.  In the past, lack of
a diversified development team and node implementation, have caused bugs to become a part of the protocol.
BCH currently has roughly two popular full-node implementations (Bitcoin ABC and Bitcoin Unlimited).
However, these implementations are forked versions of Bitcoin Core, which means they may share the same
(undiscovered) bugs.  With a diverse network of nodes, bugs in the implementation of the protocol will
result in incompatible blocks, causing a temporary fork.  This situation is healthy for the network in
the long term, as the temporary forks will resolve over time, with the intended implementation becoming
the consensus.


## Disclaimer


Bitcoin Verde has gone through weeks of testing, but v1.1.0 is still considered a Beta release.  Please
use this software with discretion.  As v1.1.0, the Bitcoin Verde node validates the entirety of the BCH
blockchain, relays new blocks and transactions, and can process upwards of 4,000 transactions per second;
however this implementation does not have the bitcoind tests run against it yet, so there may still be
implementation differences between this implementation and the reference client's derivatives.


Windows Users: Bitcoin Verde has been tested heavily on Linux and OS X.  On Windows, there may be many
issues.  On Windows, expect your node to not run as quickly due to `secp256k1` and `utxo-cache` libraries
not being cross-compiled for Windows.  Furthermore, the Windows build may not even complete its initial
block download.  If you are kind enough to run this implementation on Windows, please report any problems.

## Getting Started


To build the node, run the following:

```
./scripts/make.sh
```

This command will run the gradle command to download dependencies and build the jar file, its
configuration, and run-scripts, located within the `out` directory.


To enable and compile the btree-utxocache, configure your environment to compile C++ code; on linux
installing the 'build-essential' package should suffice. Then cd into `jni/utxo-cache` and run
`./scripts/make.sh`, and `./scripts/copy-bin.sh`.  (NOTE: These scripts are located at
`jni/utxo-cache/scripts`, not the project root).  After the build completes, cd into the project
root, and rebuild the project via `./scripts/make.sh`.


To run the node, you may `cd` into `out` and execute one of the `run-*` scripts. Or from the project
directory, run `./scripts/run.sh`, `./scripts/run-node.sh`, `./scripts/run-database.sh` etc.


Review the `conf/server.conf` and change any settings as desired.  Changes made to `conf/server.conf`
will be semi-permanent across builds, while changes made to `out/conf/server.conf` are ephemeral.


For more more detailed instructions, please refer to http://bitcoinverde.org/documentation/


## Upgrading from 1.0.x


IMPORTANT: the `make.sh` script will completely remove the `out` directory, which is where blocks
are stored.  Running this command to upgrade will cause your node to re-sync from scratch.  To
upgrade, checkout `v1.1.0` and run `./scripts/make-jar.sh`.  Restart your node via
`./scripts/rpc-shutdown.sh` and `./scripts/rpc/run-node.sh`.


## Running the Node as a Service/Daemon


The ./scripts/run-node.sh script will run the node in the current shell, which will exit upon logout.
To run the Node as a detatched process, you can start the run-node script via nohup, like so:
`touch logs/node.log && nohup ./scripts/run-node.sh >> logs/node.log & tail -f logs/node.log`
With this command you should be able to stop watching the logs with ctrl-c and may logout without
the node quitting.

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
executing the following commands and trying again:

    sudo ln -s /usr/lib/libssl.dylib /usr/local/opt/openssl/lib/libssl.1.0.0.dylib
    sudo ln -s /usr/lib/libcrypto.dylib /usr/local/opt/openssl/lib/libcrypto.1.0.0.dylib

