package com.softwareverde.bitcoin.server.message.type.node.feature;

import com.softwareverde.util.Util;

public class NodeFeatures {
    public enum Feature {
        NONE                                            ((long) (0x00 << 0x00)),
        BLOCKCHAIN_ENABLED                              ((long) (0x01 << 0x00)),
        GETUTXO_PROTOCOL_ENABLED                        ((long) (0x01 << 0x01)),
        BLOOM_CONNECTIONS_ENABLED                       ((long) (0x01 << 0x02)),
        UNUSED                                          ((long) (0x01 << 0x03)),
        XTHIN_PROTOCOL_ENABLED                          ((long) (0x01 << 0x04)),
        BITCOIN_CASH_ENABLED                            ((long) (0x01 << 0x05)),
        MINIMUM_OF_TWO_DAYS_BLOCKCHAIN_ENABLED          ((long) (0x01 << 0x0A)),
        EXTENDED_DOUBLE_SPEND_PROOFS_ENABLED            ((long) (0x01 << 0x0C)), // BitcoinVerde 2021-04-27
        UTXO_COMMITMENTS_ENABLED                        ((long) (0x01 << 0x0D)); // BitcoinVerde 2021-05-26

        public final Long value;

        Feature(final Long flag) {
            this.value = flag;
        }

        public static Feature fromString(final String string) {
            for (final Feature feature : Feature.values()) {
                if (Util.areEqual(string.toLowerCase(), feature.toString().toLowerCase())) {
                    return feature;
                }
            }

            return null;
        }
    }

    private Long _value;

    public NodeFeatures() {
        _value = Feature.NONE.value;
    }

    public NodeFeatures(final Long value) {
        _value = value;
    }

    public void setFeaturesFlags(final NodeFeatures nodeFeatures) {
        _value = nodeFeatures._value;
    }

    public void setFeatureFlags(final Long value) {
        _value = value;
    }

    public void enableFeatureFlag(final Long nodeFeatureFlag) {
        _value = (_value | nodeFeatureFlag);
    }

    public void enableFeature(final Feature feature) {
        _value = (_value | feature.value);
    }

    public Boolean isFeatureEnabled(final Feature feature) {
        if (Util.areEqual(Feature.NONE.value, feature.value)) {
            return (Util.areEqual(_value, Feature.NONE.value));
        }

        return ( ((int) (_value & feature.value)) > 0 );
    }

    public Long getFeatureFlags() {
        return _value;
    }

    public void clear() {
        _value = Feature.NONE.value;
    }

    @Override
    public String toString() {
        return String.valueOf(_value);
    }
}
