package com.softwareverde.bitcoin.server.database.query;

import com.softwareverde.bitcoin.block.merkleroot.Hashable;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.database.query.parameter.InClauseParameter;
import com.softwareverde.database.query.parameter.TypedParameter;
import com.softwareverde.security.hash.sha256.Sha256Hash;
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
            if (transactionOutputIdentifier == null) { return InClauseParameter.NULL; }

            final Sha256Hash previousTransactionHash = transactionOutputIdentifier.getTransactionHash();
            final Integer previousTransactionOutputIndex = transactionOutputIdentifier.getOutputIndex();

            final TypedParameter previousTransactionHashTypedParameter = (previousTransactionHash != null ? new TypedParameter(previousTransactionHash.getBytes()) : TypedParameter.NULL);
            final TypedParameter previousTransactionOutputIndexTypedParameter = (previousTransactionOutputIndex != null ? new TypedParameter(previousTransactionOutputIndex) : TypedParameter.NULL);

            return new InClauseParameter(previousTransactionHashTypedParameter, previousTransactionOutputIndexTypedParameter);
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

    ValueExtractor<Hashable> HASHABLE = new ValueExtractor<Hashable>() {
        @Override
        public InClauseParameter extractValues(final Hashable hashable) {
            return ValueExtractor.SHA256_HASH.extractValues(hashable != null ? hashable.getHash() : null);
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
