package com.softwareverde.bitcoin.util;

import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.util.Tuple;

public class DatabaseUtil extends com.softwareverde.database.util.DatabaseUtil {
    public interface ValueExtractor<T> {
        Tuple<Object, Object> extractValues(T value);
    }

    public static class Extractors {
        public static final ValueExtractor<TransactionOutputIdentifier> TransactionOutputIdentifier = new ValueExtractor<com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier>() {
            @Override
            public Tuple<Object, Object> extractValues(final TransactionOutputIdentifier transactionOutputIdentifier) {
                return new Tuple<Object, Object>(transactionOutputIdentifier.getTransactionHash(), transactionOutputIdentifier.getOutputIndex());
            }
        };

        public static final ValueExtractor<TransactionOutputId> TransactionOutputId = new ValueExtractor<TransactionOutputId>() {
            @Override
            public Tuple<Object, Object> extractValues(final TransactionOutputId transactionOutputIdentifier) {
                return new Tuple<Object, Object>(transactionOutputIdentifier.getTransactionId(), transactionOutputIdentifier.getOutputIndex());
            }
        };
    }

    protected DatabaseUtil() { }

    protected static void buildTupleString(final StringBuilder stringBuilder, final Object value0, final Object value1) {
        stringBuilder.append("('");
        stringBuilder.append(value0);
        stringBuilder.append("','");
        stringBuilder.append(value1);
        stringBuilder.append("')");
    }

    public static String createInClause(final Iterable<?> list) {
        final String inClause = _createInClause(list, null);
        return (inClause.isEmpty() ? "NULL" : inClause);
    }

    public static <T> String createInClause(final List<T> values, final ValueExtractor<T> valueExtractor) {
        final StringBuilder stringBuilder = new StringBuilder();

        String prefix = "";
        for (final T item : values) {
            stringBuilder.append(prefix);

            final Tuple<Object, Object> tupleValues = valueExtractor.extractValues(item);
            DatabaseUtil.buildTupleString(stringBuilder, tupleValues.first, tupleValues.second);

            prefix = ",";
        }

        return stringBuilder.toString();
    }
}
