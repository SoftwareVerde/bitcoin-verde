package com.softwareverde.bitcoin.server.stratum.message.server;

import com.softwareverde.bitcoin.server.stratum.message.ResponseMessage;

public class MinerSubmitBlockResult extends ResponseMessage {
    public MinerSubmitBlockResult(final Integer messageId, final Boolean wasAccepted) {
        super(messageId);
        _result = (wasAccepted ? RESULT_TRUE : RESULT_FALSE);
    }
}
