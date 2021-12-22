package com.softwareverde.bitcoin.server.stratum.message.server;

import com.softwareverde.bitcoin.server.stratum.message.ResponseMessage;

public class MinerSubmitBlockResult extends ResponseMessage {
    public MinerSubmitBlockResult(final Integer messageId) {
        super(messageId);
        _result = RESULT_TRUE;
    }

    public MinerSubmitBlockResult(final Integer messageId, final Error error) {
        super(messageId);

        _result = (error != null ? RESULT_FALSE : RESULT_TRUE);
        _error = error;
    }
}
