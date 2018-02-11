package com.softwareverde.bitcoin.server.message.type.node.feature;

public class NodeFeatures {
    public static class Flags {
        public static Long NONE                         = (long) (0x00 << 0x00);
        public static Long BLOCKCHAIN_ENABLED           = (long) (0x01 << 0x00);
        public static Long GETUTXO_PROTOCOL_ENABLED     = (long) (0x01 << 0x01);
        public static Long BLOOM_CONNECTIONS_ENABLED    = (long) (0x01 << 0x02);
        // public static Long UNUSED                       = (long) (0x01 << 0x03);
        public static Long XTHIN_PROTOCOL_ENABLED       = (long) (0x01 << 0x04);
        public static Long BITCOIN_CASH_ENABLED         = (long) (0x01 << 0x05);
    }

    private Long _value;

    public NodeFeatures() {
        _value = Flags.NONE;
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

    public Boolean hasFeatureFlagEnabled(final Long nodeFeatureFlag) {
        if (Flags.NONE.equals(nodeFeatureFlag)) {
            return (_value.equals(Flags.NONE));
        }

        return ( ((int) (_value & nodeFeatureFlag)) > 0 );
    }

    public Long getFeatureFlags() {
        return _value;
    }

    public void clear() {
        _value = Flags.NONE;
    }
}
