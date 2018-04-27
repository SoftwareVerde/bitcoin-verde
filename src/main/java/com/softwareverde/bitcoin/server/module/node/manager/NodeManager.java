package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.async.HaltableThread;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.message.type.node.address.NodeIpAddress;
import com.softwareverde.bitcoin.server.module.node.manager.health.NodeHealth;
import com.softwareverde.bitcoin.server.node.Node;
import com.softwareverde.bitcoin.server.node.NodeId;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Container;

import java.util.*;

public class NodeManager {
    public static final Long REQUEST_TIMEOUT_THRESHOLD = 5_000L;

    protected final Object _mutex = new Object();

    protected final Map<NodeId, Node> _nodes = new HashMap<NodeId, Node>();
    protected final Map<NodeId, NodeHealth> _nodeHealthMap = new HashMap<NodeId, NodeHealth>();

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
                        final NodeId newNodeId = newNode.getId();
                        _nodes.put(newNodeId, newNode);
                        _nodeHealthMap.put(newNodeId, new NodeHealth(newNodeId));
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

        final List<NodeHealth> nodeHealthList = new ArrayList<NodeHealth>(activeNodeCount);
        for (final Node activeNode : activeNodes) {
            final NodeHealth nodeHealth = _nodeHealthMap.get(activeNode.getId());
            nodeHealthList.add(nodeHealth);
        }
        Collections.sort(nodeHealthList, NodeHealth.COMPARATOR);

        final NodeHealth bestNodeHealth = nodeHealthList.get(nodeHealthList.size() - 1);
        final Node selectedNode = _nodes.get(bestNodeHealth.getNodeId());

        // for (final NodeHealth nodeHealth : nodeHealthList) {
        //     Logger.log("Node Health: "+ nodeHealth.getNodeId() + " = " +nodeHealth.calculateHealth());
        // }

        Logger.log("Selected Node: " + (selectedNode.getId()) + " (" + bestNodeHealth.calculateHealth() + "hp) - " + (selectedNode.getConnectionString()) + " - " + activeNodeCount + " / " + _nodes.size());

        return selectedNode;
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
            // final NodeId nodeId = idleNode.getId();
            // _nodeHealthMap.get(nodeId).onMessageSent();

            idleNode.ping(new Node.PingCallback() {
                @Override
                public void onResult(final Long pingInMilliseconds) {
                    Logger.log("*** Node Pong: " + pingInMilliseconds);
                }
            });
            Logger.log("*** Pinging Idle Node: " + idleNode.getConnectionString());
        }
    }

    protected Thread _createTimeoutThread(final Container<Boolean> didMessageTimeOut, final NodeHealth nodeHealth) {
        final Thread timeoutThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try { Thread.sleep(REQUEST_TIMEOUT_THRESHOLD); }
                catch (final Exception exception) { return; }

                if (didMessageTimeOut.value) {
                    nodeHealth.onMessageReceived(false);
                }
            }
        });

        return timeoutThread;
    }

    public NodeManager() {
        _nodeMaintenanceThread.setSleepTime(10000L);
    }

    public void addNode(final Node node) {
        _initNode(node);

        synchronized (_mutex) {
            final NodeId nodeId = node.getId();
            _nodes.put(nodeId, node);
            _nodeHealthMap.put(nodeId, new NodeHealth(nodeId));
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
            Logger.log("Queuing Command: NodeManager.requestBlock "+ blockHash);

            final Runnable queuedInvocation = new Runnable() {
                @Override
                public void run() {
                    Logger.log("Executing Command: NodeManager.requestBlock "+ blockHash);
                    NodeManager.this.requestBlock(blockHash, downloadBlockCallback);
                }
            };

            synchronized (_mutex) {
                _queuedNodeRequests.add(queuedInvocation);
            }

            return;
        }

        final NodeId nodeId = selectedNode.getId();
        final NodeHealth nodeHealth = _nodeHealthMap.get(nodeId);
        final Container<Boolean> didMessageTimeOut = new Container<Boolean>(true);
        final Thread timeoutThread = _createTimeoutThread(didMessageTimeOut, nodeHealth);
        nodeHealth.onMessageSent();
        selectedNode.requestBlock(blockHash, new Node.DownloadBlockCallback() {
            @Override
            public void onResult(final Block result) {
                nodeHealth.onMessageReceived(true);
                didMessageTimeOut.value = false;
                timeoutThread.interrupt();

                if (downloadBlockCallback != null) {
                    downloadBlockCallback.onResult(result);
                }
            }
        });
        timeoutThread.start();
    }

    public void requestBlockHashesAfter(final Sha256Hash blockHash, final Node.QueryCallback queryCallback) {
        final Node selectedNode = _selectNode();

        if (selectedNode == null) {
            Logger.log("Queuing Command: NodeManager.requestBlockHashesAfter "+ blockHash);
            final Runnable queuedInvocation = new Runnable() {
                @Override
                public void run() {
                    Logger.log("Executing Command: NodeManager.requestBlockHashesAfter "+ blockHash);
                    NodeManager.this.requestBlockHashesAfter(blockHash, queryCallback);
                }
            };

            synchronized (_mutex) {
                _queuedNodeRequests.add(queuedInvocation);
            }

            return;
        }

        final NodeId nodeId = selectedNode.getId();
        final NodeHealth nodeHealth = _nodeHealthMap.get(nodeId);
        final Container<Boolean> didMessageTimeOut = new Container<Boolean>(true);
        final Thread timeoutThread = _createTimeoutThread(didMessageTimeOut, nodeHealth);
        nodeHealth.onMessageSent();
        selectedNode.requestBlockHashesAfter(blockHash, new Node.QueryCallback() {
            @Override
            public void onResult(final List<Sha256Hash> result) {
                nodeHealth.onMessageReceived(true);
                didMessageTimeOut.value = false;
                timeoutThread.interrupt();

                if (queryCallback != null) {
                    queryCallback.onResult(result);
                }
            }
        });
        timeoutThread.start();
    }

}
