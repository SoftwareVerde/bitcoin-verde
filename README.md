# Bitcoin-Verde v0.0.1


## Description


Bitcoin-Verde is a ground-up implementation of the Bitcoin (Cash) (BCH) protocol.  This project is intended
to be(come) a viable implementaion a BCH mining node, a full node, blockchain explorer, and library.


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


Currently (as of v0.0.1), this implementation is very far from complete, and still a ways away from use
in-production.  In fact, it is very unlikely that the full blockchain will even verify against this
implementation since many OpCodes are still left unimplemented.  This project is currently great for
educational purposes and research, however, mining with this node in its current state is mostly a waste
of time.  Furthermore, this node is currently a read-only node connecting to a single peer located at:
btc.softwareverde.com -- which is a Bitcoin ABC full-node hosted by Software Verde, LLC.


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
will be semi-permanant across builds, while changes made to `out/conf/server.conf` are ephemeral.


## Contributions


Any contributions are welcomed and will be reviewed via pull-requests.  In order to be accepted,
care must be taken for all immutable classes and their mutable counterparts.  Additionally, PR
resolving bugs shall only be accepted along with a test proving their existance and fix.


## Contact


Feel free to contact Software Verde, LLC at any appropriate softwareverde.com email address.
Generic enquiries may be directed at bitcoin@softwareverde.com

