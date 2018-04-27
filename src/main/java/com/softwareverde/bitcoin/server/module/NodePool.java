package com.softwareverde.bitcoin.server.module;

import com.softwareverde.async.HaltableThread;
import com.softwareverde.bitcoin.server.message.type.node.address.NodeIpAddress;
import com.softwareverde.bitcoin.server.node.Node;
import com.softwareverde.bitcoin.server.node.NodeId;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;

import java.util.HashMap;
import java.util.Map;

public class NodePool {
    protected final Object _mutex = new Object();

    protected final Map<NodeId, Node> _nodes = new HashMap<NodeId, Node>();
    protected final MutableList<Runnable> _queuedNodeRequests = new MutableList<Runnable>();

    protected final HaltableThread _nodeMaintenanceThread = new HaltableThread(new Runnable() {
        @Override
        public void run() {
            _pingIdleNodes();
        }
    });

    protected void _initNode(final Node node) {
        node.setNodeAddressesReceivedCallback(new Node.NodeAddressesReceivedCallback() {
            @Override
            public void onNewNodeAddress(final NodeIpAddress nodeIpAddress) {
                final String address = nodeIpAddress.getIp().toString();
                final Integer port = nodeIpAddress.getPort();
                final String connectionString = (address + ":" + port);

                synchronized (_mutex) {
                    for (final Node existingNode : _nodes.values()) {
                        final Boolean isAlreadyConnectedToNode = (existingNode.getConnectionString().equals(connectionString));
                        if (isAlreadyConnectedToNode) {
                            return;
                        }
                    }

                    final Node newNode = new Node(address, port);
                    _initNode(newNode);

                    synchronized (_mutex) {
                        _nodes.put(newNode.getId(), newNode);
                    }
                }
            }
        });

        node.setNodeConnectedCallback(new Node.NodeConnectedCallback() {
            @Override
            public void onNodeConnected() {
                if (! node.hasActiveConnection()) { return; }

                synchronized (_mutex) {
                    for (final Runnable runnable : _queuedNodeRequests) {
                        new Thread(runnable).start();
                    }
                    _queuedNodeRequests.clear();
                }
            }
        });

        node.setNodeHandshakeCompleteCallback(new Node.NodeHandshakeCompleteCallback() {
            @Override
            public void onHandshakeComplete() {
                Logger.log("Handshake Callback.");

                for (final Runnable runnable : _queuedNodeRequests) {
                    new Thread(runnable).start();
                }
                _queuedNodeRequests.clear();
            }
        });

        node.setNodeDisconnectedCallback(new Node.NodeDisconnectedCallback() {
            @Override
            public void onNodeDisconnected() {
                synchronized (_mutex) {
                    _nodes.remove(node.getId());
                }
            }
        });
    }

    // TODO: Implement NodeHealth...
    protected Node _selectNode() {
        final MutableList<Node> activeNodes;
        synchronized (_mutex) {
            activeNodes = new MutableList<Node>(_nodes.size());
            for (final Node node : _nodes.values()) {
                if (node.hasActiveConnection()) {
                    activeNodes.add(node);
                }
            }
        }

        final Integer activeNodeCount = activeNodes.getSize();
        if (activeNodeCount == 0) { return null; }

        final Integer selectedNodeIndex = ( ((int) (Math.random() * 7777)) % activeNodeCount );

        Logger.log("Select Node: " + (activeNodes.get(selectedNodeIndex).getConnectionString()) + " - " + activeNodeCount + " / " + _nodes.size());

        return activeNodes.get(selectedNodeIndex);
    }

    protected void _pingIdleNodes() {
        final Long maxIdleTime = 30000L;

        final Long now = System.currentTimeMillis();

        final MutableList<Node> idleNodes;
        synchronized (_mutex) {
            idleNodes = new MutableList<Node>(_nodes.size());
            for (final Node node : _nodes.values()) {
                final Long lastMessageTime = node.getLastMessageReceivedTimestamp();
                final Long idleDuration = (now - lastMessageTime); // NOTE: Race conditions could result in a negative value...

                if (idleDuration > maxIdleTime) {
                    idleNodes.add(node);
                }
            }
        }

        Logger.log("Idle Node Count: " + idleNodes.getSize() + " / " + _nodes.size());
        for (final Node idleNode : idleNodes) {
            idleNode.ping();
            Logger.log("*** Pinging Idle Node: " + idleNode.getConnectionString());
        }
    }

    public NodePool() {
        _nodeMaintenanceThread.setSleepTime(10000L);
    }

    public void addNode(final Node node) {
        _initNode(node);

        synchronized (_mutex) {
            _nodes.put(node.getId(), node);
        }
    }

    public void startNodeMaintenanceThread() {
        _nodeMaintenanceThread.start();
    }

    public void stopNodeMaintenanceThread() {
        _nodeMaintenanceThread.halt();
    }

    public void requestBlock(final Sha256Hash blockHash, final Node.DownloadBlockCallback downloadBlockCallback) {
        final Node selectedNode = _selectNode();

        if (selectedNode == null) {
            Logger.log("Queuing Command: NodePool.requestBlock "+ blockHash);

            final Runnable queuedInvocation = new Runnable() {
                @Override
                public void run() {
                    Logger.log("Executing Command: NodePool.requestBlock "+ blockHash);
                    NodePool.this.requestBlock(blockHash, downloadBlockCallback);
                }
            };

            synchronized (_mutex) {
                _queuedNodeRequests.add(queuedInvocation);
            }

            return;
        }

        selectedNode.requestBlock(blockHash, downloadBlockCallback);
    }

    public void requestBlockHashesAfter(final Sha256Hash blockHash, final Node.QueryCallback queryCallback) {
        final Node selectedNode = _selectNode();

        if (selectedNode == null) {
            Logger.log("Queuing Command: NodePool.requestBlockHashesAfter "+ blockHash);
            final Runnable queuedInvocation = new Runnable() {
                @Override
                public void run() {
                    Logger.log("Executing Command: NodePool.requestBlockHashesAfter "+ blockHash);
                    NodePool.this.requestBlockHashesAfter(blockHash, queryCallback);
                }
            };

            synchronized (_mutex) {
                _queuedNodeRequests.add(queuedInvocation);
            }

            return;
        }

        selectedNode.requestBlockHashesAfter(blockHash, queryCallback);
    }

}
