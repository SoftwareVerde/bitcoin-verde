package com.softwareverde.bitcoin.util;

import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;

public class DatabaseUtil extends com.softwareverde.database.util.DatabaseUtil {
    protected DatabaseUtil() { }

    public static String createInTupleClause(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) {
        final StringBuilder stringBuilder = new StringBuilder();

        String prefix = "";
        for (final TransactionOutputIdentifier item : transactionOutputIdentifiers) {
            stringBuilder.append(prefix);
            stringBuilder.append("('");
            stringBuilder.append(item.getTransactionHash());
            stringBuilder.append("',");
            stringBuilder.append(item.getOutputIndex());
            stringBuilder.append(")");
            prefix = ",";
        }

        return stringBuilder.toString();
    }
}
