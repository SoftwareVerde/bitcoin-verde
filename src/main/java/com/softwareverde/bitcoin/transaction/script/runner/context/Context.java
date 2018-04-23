package com.softwareverde.bitcoin.transaction.script.runner.context;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.constable.Constable;

public interface Context extends Constable<ImmutableContext> {
    Long getBlockHeight();
    TransactionInput getTransactionInput();
    TransactionOutput getTransactionOutput();
    Transaction getTransaction();
    Integer getTransactionInputIndex();

    Script getCurrentScript();

    /**
     * Returns the index of the script's current execution index.
     *  Calling this function during the execution of the first opcode will return zero.
     */
    Integer getCurrentScriptIndex();

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
}
