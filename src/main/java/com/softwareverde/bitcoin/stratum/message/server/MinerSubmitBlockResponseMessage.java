package com.softwareverde.bitcoin.server.stratum.message.server;

import com.softwareverde.bitcoin.server.stratum.message.ResponseMessage;

public class MinerSubmitBlockResponseMessage extends ResponseMessage {
    public MinerSubmitBlockResponseMessage(final Integer messageId) {
        super(messageId);
        _result = RESULT_TRUE;
    }

    public MinerSubmitBlockResponseMessage(final Integer messageId, final Error error) {
        super(messageId);

        _result = (error != null ? RESULT_FALSE : RESULT_TRUE);
        _error = error;
    }
}
