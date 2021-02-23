package com.softwareverde.bitcoin.server.node;

import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

public class FailableRequest {
    protected static final SystemTime SYSTEM_TIME = new SystemTime();
    protected static final Runnable DO_NOTHING = new Runnable() {
        @Override
        public void run() { }
    };

    final String requestDescription;
    final Long requestStartTimeMs;
    final Long startingByteCountReceived;
    final BitcoinNode.BitcoinNodeCallback callback;
    final Runnable onFailure;

    public FailableRequest(final String requestDescription, final Long startingByteCountReceived, final BitcoinNode.BitcoinNodeCallback callback) {
        this(requestDescription, startingByteCountReceived, callback, DO_NOTHING);
    }

    public FailableRequest(final String requestDescription, final Long startingByteCountReceived, final BitcoinNode.BitcoinNodeCallback callback, final Runnable onFailure) {
        this.requestDescription = requestDescription;
        this.requestStartTimeMs = SYSTEM_TIME.getCurrentTimeInMilliSeconds();
        this.startingByteCountReceived = startingByteCountReceived;
        this.callback = callback;
        this.onFailure = Util.coalesce(onFailure, DO_NOTHING);
    }

    public void onFailure() {
        if (this.onFailure != null) {
            this.onFailure.run();
        }
    }
}
