package com.softwareverde.bitcoin.server.database.query;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.work.Work;
import com.softwareverde.bitcoin.hash.Hash;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.database.query.parameter.TypedParameter;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.util.type.identifier.Identifier;

public class Query extends com.softwareverde.database.query.Query {
    public Query(final String query) {
        super(query);
    }

    @Override
    public Query setParameter(final Boolean value) {
        super.setParameter(value);
        return this;
    }

    @Override
    public Query setParameter(final Long value) {
        super.setParameter(value);
        return this;
    }

    @Override
    public Query setParameter(final Integer value) {
        super.setParameter(value);
        return this;
    }

    @Override
    public Query setParameter(final Short value) {
        super.setParameter(value);
        return this;
    }

    @Override
    public Query setParameter(final Double value) {
        super.setParameter(value);
        return this;
    }

    @Override
    public Query setParameter(final Float value) {
        super.setParameter(value);
        return this;
    }

    @Override
    public Query setParameter(final byte[] value) {
        super.setParameter(value);
        return this;
    }

    @Override
    public Query setParameter(final ByteArray value) {
        super.setParameter(value);
        return this;
    }

    @Override
    public Query setParameter(final Identifier value) {
        super.setParameter(value);
        return this;
    }

    @Override
    public Query setParameter(final TypedParameter value) {
        super.setParameter(value);
        return this;
    }

    @Override
    public Query setParameter(final String value) {
        super.setParameter(value);
        return this;
    }

    public Query setParameter(final Hash value) {
        super.setParameter(value != null ? value.getBytes() : null);
        return this;
    }

    public Query setParameter(final SequenceNumber value) {
        super.setParameter(value != null ? value.toString() : null);
        return this;
    }

    public Query setParameter(final Ip value) {
        super.setParameter(value != null ? value.toString() : null);
        return this;
    }

    public Query setParameter(final NodeFeatures.Feature value) {
        super.setParameter(value != null ? value.toString() : null);
        return this;
    }

    public Query setParameter(final Difficulty value) {
        final ByteArray byteArray = (value != null ? value.encode() : null);
        super.setParameter(byteArray != null ? byteArray.getBytes() : null);
        return this;
    }

    public Query setParameter(final Work value) {
        super.setParameter(value != null ? value.getBytes() : null);
        return this;
    }
}
