package com.softwareverde.bitcoin.transaction.script.runner.context;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.constable.Constable;
import com.softwareverde.json.Jsonable;

public interface TransactionContext extends Constable<ImmutableTransactionContext>, Jsonable {
    Long getBlockHeight();
    MedianBlockTime getMedianBlockTime();
    TransactionInput getTransactionInput();
    TransactionOutput getTransactionOutput();

    /**
     * Returns the Transaction being validated.
     */
    Transaction getTransaction();

    Integer getTransactionInputIndex();

    /**
     * Returns the script that is currently being evaluated.
     *  This script could be one of many things:
     *      - The Tx Input's Unlocking Script
     *      - The Tx Output's Locking Script
     *      - The Tx P2SH
     */
    Script getCurrentScript();

    /**
     * Returns the index of the script's current execution index.
     *  Calling this function during the execution of the first opcode will return zero.
     */
    Integer getScriptIndex();

    /**
     * Returns the index within script that starts with the index immediately after the last executed CODE_SEPARATOR operation.
     *  If the script has not encountered a CodeSeparator, the return value will be zero.
     *  Therefore, calling Script.subScript() with this return value will always be safe.
     *
     * Ex: Given:
     *      ix:         0     | 1     | 2              | 3
     *      opcodes:    NO_OP | NO_OP | CODE_SEPARATOR | NO_OP
     *  Context.getScriptLastCodeSeparatorIndex() will return: 3
     */
    Integer getScriptLastCodeSeparatorIndex();

    /**
     * Returns the total number of Signature operations executed thus far, as defined by HF20200515.
     */
    Integer getSignatureOperationCount();

    UpgradeSchedule getUpgradeSchedule();
}
