package com.softwareverde.bitcoin.server.module.node.rpc.core;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.logging.Logger;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

public class ZmqNotificationThread extends Thread {
    protected final NotificationType _notificationType;
    protected NotificationCallback _callback;

    public ZmqNotificationThread(final NotificationType notificationType, final String endpointUri, final NotificationCallback callback) {
        super(new Runnable() {
            @Override
            public void run() {
                try (final ZContext context = new ZContext()) {
                    final SocketType socketType = SocketType.type(zmq.ZMQ.ZMQ_SUB);
                    final ZMQ.Socket socket = context.createSocket(socketType);

                    Logger.trace("Listening to: " + endpointUri + " for " + notificationType);
                    socket.connect(endpointUri); // eg: "tcp://host:port"

                    for (final NotificationType notificationType : NotificationType.values()) {
                        final String subscriptionString = ZmqMessageTypeConverter.toSubscriptionString(notificationType);
                        if (subscriptionString != null) {
                            socket.subscribe(subscriptionString.getBytes());
                        }
                    }

                    final Thread thread = Thread.currentThread();
                    while (! thread.isInterrupted()) {
                        final ZMsg zMsg = ZMsg.recvMsg(socket);
                        if (zMsg == null) { break; }

                        NotificationType notificationType = null;
                        ByteArray payload = null;

                        int frameIndex = 0;
                        for (final ZFrame zFrame : zMsg) {
                            final ByteArray bytes = ByteArray.wrap(zFrame.getData());
                            if (frameIndex == 0) {
                                notificationType = ZmqMessageTypeConverter.fromMessageBytes(bytes);
                            }
                            else if (frameIndex == 1) {
                                payload = bytes;
                            }
                            else {
                                break;
                            }
                            ++frameIndex;
                        }

                        if ( (notificationType != null) && (payload != null) ) {
                            Logger.trace(endpointUri + " - " + notificationType + ": " + payload);
                            final Notification notification = new Notification(notificationType, payload);
                            callback.onNewNotification(notification);
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

        _notificationType = notificationType;
        _callback = callback;
    }

    public NotificationType getMessageType() {
        return _notificationType;
    }
}
