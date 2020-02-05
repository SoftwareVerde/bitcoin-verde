package com.softwareverde.bitcoin.server.database.query;

import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.database.query.ParameterFactoryCore;
import com.softwareverde.database.query.parameter.TypedParameter;

public class ParameterFactory extends ParameterFactoryCore {

    @Override
    public TypedParameter fromObject(final Object object) {
        if (object instanceof NodeFeatures.Feature) {
            return new TypedParameter(object.toString());
        }
        return super.fromObject(object);
    }
}
