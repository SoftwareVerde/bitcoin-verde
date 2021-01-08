package com.softwareverde.bitcoin.rpc.core.zmq;

import com.softwareverde.bitcoin.rpc.RpcNotification;
import com.softwareverde.bitcoin.rpc.RpcNotificationCallback;
import com.softwareverde.bitcoin.rpc.RpcNotificationType;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.logging.Logger;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

public class ZmqNotificationThread extends Thread {
    protected final RpcNotificationType _rpcNotificationType;
    protected RpcNotificationCallback _callback;

    public ZmqNotificationThread(final RpcNotificationType rpcNotificationType, final String endpointUri, final RpcNotificationCallback callback) {
        super(new Runnable() {
            @Override
            public void run() {
                try (final ZContext context = new ZContext()) {
                    final SocketType socketType = SocketType.type(zmq.ZMQ.ZMQ_SUB);
                    final ZMQ.Socket socket = context.createSocket(socketType);

                    Logger.trace("Listening to: " + endpointUri + " for " + rpcNotificationType);
                    socket.connect(endpointUri); // eg: "tcp://host:port"

                    for (final RpcNotificationType rpcNotificationType : RpcNotificationType.values()) {
                        final String subscriptionString = ZmqMessageTypeConverter.toSubscriptionString(rpcNotificationType);
                        if (subscriptionString != null) {
                            socket.subscribe(subscriptionString.getBytes());
                        }
                    }

                    final Thread thread = Thread.currentThread();
                    while (! thread.isInterrupted()) {
                        final ZMsg zMsg = ZMsg.recvMsg(socket);
                        if (zMsg == null) { break; }

                        RpcNotificationType rpcNotificationType = null;
                        ByteArray payload = null;

                        int frameIndex = 0;
                        for (final ZFrame zFrame : zMsg) {
                            final ByteArray bytes = ByteArray.wrap(zFrame.getData());
                            if (frameIndex == 0) {
                                rpcNotificationType = ZmqMessageTypeConverter.fromMessageBytes(bytes);
                            }
                            else if (frameIndex == 1) {
                                payload = bytes;
                            }
                            else {
                                break;
                            }
                            ++frameIndex;
                        }

                        if ( (rpcNotificationType != null) && (payload != null) ) {
                            Logger.trace(endpointUri + " - " + rpcNotificationType + ": " + payload);
                            final RpcNotification rpcNotification = new RpcNotification(rpcNotificationType, payload);
                            callback.onNewNotification(rpcNotification);
                        }
                    }
                }
            }
        });

        this.setName("ZMQ Notification Thread - " + endpointUri);
        this.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread thread, final Throwable exception) {
                Logger.debug(exception);
            }
        });
        this.setDaemon(true);

        _rpcNotificationType = rpcNotificationType;
        _callback = callback;
    }

    public RpcNotificationType getMessageType() {
        return _rpcNotificationType;
    }
}
