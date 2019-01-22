package com.softwareverde.database.mysql;

import com.softwareverde.database.Query;
import com.softwareverde.io.Logger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BatchedUpdateQuery extends Query {
    public BatchedUpdateQuery(final String query) {
        super(query);
    }

    @Override
    public String getQueryString() {
        // UPDATE t SET v = x WHERE z IN (?)
        final Integer parameterCount = _parameters.size();
        final Integer nonInClauseParameterCount = (_query.length() - _query.replace("?", "").length()) - 1;
        final Integer batchCount = (parameterCount - nonInClauseParameterCount);

        if (nonInClauseParameterCount < 0) {
            Logger.log("Invalid parameter count for batched update query: " + _query);
            return null;
        }

        final String queryStart;
        final String queryEnd;
        {
            final Matcher matcher = Pattern.compile("IN[ ]*\\(\\?\\)").matcher(_query);
            if (! matcher.find()) {
                Logger.log("Could not find IN clause for query: " + _query);
                return null;
            }

            final Integer startIndex = matcher.start();
            final Integer endIndex = matcher.end();

            queryStart = _query.substring(0, startIndex);
            queryEnd = _query.substring(endIndex);
        }

        final StringBuilder stringBuilder = new StringBuilder(queryStart);
        stringBuilder.append("IN (");

        for (int i = 0; i < batchCount; ++i) {
            stringBuilder.append("?");
            if (i < batchCount - 1) {
                stringBuilder.append(", ");
            }
        }

        stringBuilder.append(")");
        stringBuilder.append(queryEnd);

        return stringBuilder.toString();
    }

    public void clear() {
        _parameters.clear();
    }
}
