package com.softwareverde.bitcoin.server.database.query;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.database.query.parameter.InClauseParameter;
import com.softwareverde.database.query.parameter.TypedParameter;
import com.softwareverde.util.type.identifier.Identifier;

public interface ValueExtractor<T> extends com.softwareverde.database.query.ValueExtractor<T> {

    ValueExtractor<Identifier> IDENTIFIER = new ValueExtractor<Identifier>() {
        @Override
        public InClauseParameter extractValues(final Identifier identifier) {
            final TypedParameter typedParameter = (identifier != null ? new TypedParameter(identifier.longValue()) : TypedParameter.NULL);
            return new InClauseParameter(typedParameter);
        }
    };

    ValueExtractor<TransactionOutputIdentifier> TRANSACTION_OUTPUT_IDENTIFIER = new ValueExtractor<TransactionOutputIdentifier>() {
        @Override
        public InClauseParameter extractValues(final TransactionOutputIdentifier transactionOutputIdentifier) {
            final TypedParameter typedParameter0 = (transactionOutputIdentifier != null ? new TypedParameter(transactionOutputIdentifier.getTransactionHash().getBytes()) : TypedParameter.NULL);
            final TypedParameter typedParameter1 = (transactionOutputIdentifier != null ? new TypedParameter(transactionOutputIdentifier.getOutputIndex()) : TypedParameter.NULL);
            return new InClauseParameter(typedParameter0, typedParameter1);
        }
    };

    ValueExtractor<TransactionOutputId> TRANSACTION_OUTPUT_ID = new ValueExtractor<TransactionOutputId>() {
        @Override
        public InClauseParameter extractValues(final TransactionOutputId transactionOutputId) {
            final TypedParameter typedParameter0 = (transactionOutputId != null ? new TypedParameter(transactionOutputId.getTransactionId().longValue()) : TypedParameter.NULL);
            final TypedParameter typedParameter1 = (transactionOutputId != null ? new TypedParameter(transactionOutputId.getOutputIndex()) : TypedParameter.NULL);
            return new InClauseParameter(typedParameter0, typedParameter1);
        }
    };

    ValueExtractor<ByteArray> BYTE_ARRAY = new ValueExtractor<ByteArray>() {
        @Override
        public InClauseParameter extractValues(final ByteArray byteArray) {
            final TypedParameter typedParameter = (byteArray != null ? new TypedParameter(byteArray.getBytes()) : TypedParameter.NULL);
            return new InClauseParameter(typedParameter);
        }
    };

    ValueExtractor<Sha256Hash> SHA256_HASH = new ValueExtractor<Sha256Hash>() {
        @Override
        public InClauseParameter extractValues(final Sha256Hash sha256Hash) {
            final TypedParameter typedParameter = (sha256Hash != null ? new TypedParameter(sha256Hash.getBytes()) : TypedParameter.NULL);
            return new InClauseParameter(typedParameter);
        }
    };

    ValueExtractor<NodeFeatures.Feature> NODE_FEATURE = new ValueExtractor<NodeFeatures.Feature>() {
        @Override
        public InClauseParameter extractValues(final NodeFeatures.Feature nodeFeature) {
            final TypedParameter typedParameter = (nodeFeature != null ? new TypedParameter(nodeFeature.toString()) : TypedParameter.NULL);
            return new InClauseParameter(typedParameter);
        }
    };
}
