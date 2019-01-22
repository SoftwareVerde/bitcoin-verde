
# Bitcoin Verde v1.0.2


## Updates

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
full node, blockchain explorer, and library.


## Purpose


Bitcoin (Core) (BTC) is the sole group responsible for development on BTC's client.  In the past, lack of
a diversified development team and node implementation, have caused bugs to become a part of the protocol.
BCH currently has roughly three full-node implementations (Bitcoin ABC, Bitcoin XT, Bitcoin Unlimited).
However, these implementations are forked versions of Bitcoin Core, which means they may share the same
(undiscovered) bugs.  With a diverse network of nodes, bugs in the implementation of the protocol will
result in incompatible blocks, causing a temporary fork.  This situation is healthy for the network in
the long term, as the temporary forks will resolve over time, with the intended implementation becoming
the consensus.


## Disclaimer


Bitcoin Verde has gone through weeks of testing, but v1.0.0 is still considered a Beta release.  Please
use this software with discretion.  As v1.0.0, the Bitcoin Verde node validates the entirety of the BCH
blockchain, relays new blocks and transactions, and can process upwards of 4,000 transactions per second;
however this implementation does not have the bitcoind tests run against it yet, so there may still be
implementation differences between this implementation and the reference client's derivatives.


Windows Users: Bitcoin Verde has been tested heavily on Linux and OS X.  On Windows, there may be many
issues.  On Windows, expect your node to not run as quickly due to `secp256k1` and `utxo-cache` libraries
not being cross-compiled for Windows.  Furthermore, the Windows build may not even complete its initial
block download.  If you are kind enough to run this implementation on windows, please report any problems.

## Getting Started


To build the node, run the following:

```
./scripts/make.sh
```

This command will run the gradle command to download dependencies and build the jar file, its
configuration, and run-scripts, located within the `out` directory.


To run the node, you may `cd` into `out` and execute one of the `run-*` scripts. Or from the project
directory, run `./scripts/run.sh`, `./scripts/run-node.sh`, `./scripts/run-database.sh` etc.


Review the `conf/server.conf` and change any settings as desired.  Changes made to `conf/server.conf`
will be semi-permanent across builds, while changes made to `out/conf/server.conf` are ephemeral.


For more more detailed instructions, please refer to http://bitcoinverde.org/documentation/


## Contributions


Any contributions are welcomed and will be reviewed via pull-requests.  In order to be accepted,
care must be taken for all immutable classes and their mutable counterparts.  Additionally, PR
resolving bugs preferrably be accepted along with a test proving their existence and fix.


## Contact


Feel free to contact Software Verde, LLC at any appropriate softwareverde.com email address.
Generic enquiries may be directed at bitcoin-verde@softwareverde.com
