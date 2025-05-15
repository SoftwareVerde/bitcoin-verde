package com.softwareverde.bitcoin.server.node;

import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.constable.map.mutable.MutableMap;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.p2p.message.ProtocolMessage;

public class MessageRouter {
    public interface MessageHandler {
        void run(ProtocolMessage message, BitcoinNode bitcoinNode);
    }

    public interface UnknownRouteHandler {
        void run(MessageType messageType, ProtocolMessage message, BitcoinNode bitcoinNode);
    }

    protected final MutableMap<MessageType, MessageHandler> _routingTable = new MutableHashMap<>();
    protected UnknownRouteHandler _unknownRouteHandler;

    public void addRoute(final MessageType messageType, final MessageHandler messageHandler) {
        _routingTable.put(messageType, messageHandler);
    }

    public void removeRoute(final MessageType messageType) {
        _routingTable.remove(messageType);
    }

    public void route(final MessageType messageType, final ProtocolMessage message, final BitcoinNode bitcoinNode) {
        final MessageHandler messageHandler = _routingTable.get(messageType);
        if (messageHandler == null) {
            if (_unknownRouteHandler != null) { _unknownRouteHandler.run(messageType, message, bitcoinNode); }
            return;
        }

        try {
            messageHandler.run(message, bitcoinNode);
        }
        catch (final Exception exception) {
            Logger.warn(exception);
        }
    }

    public void setUnknownRouteHandler(final UnknownRouteHandler unknownRouteHandler) {
        _unknownRouteHandler = unknownRouteHandler;
    }
}
