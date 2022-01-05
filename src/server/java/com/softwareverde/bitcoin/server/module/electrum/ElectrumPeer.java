package com.softwareverde.bitcoin.server.module.electrum;

import com.softwareverde.bitcoin.server.module.electrum.json.ElectrumJson;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;

class ElectrumPeer implements Jsonable {
    public final Ip ip;
    public final String host;
    public final Integer tcpPort;
    public final Integer tlsPort;
    public final List<String> otherFeatures;

    protected static Tuple<Integer, Integer> parsePorts(final List<String> features) {
        final Tuple<Integer, Integer> ports = new Tuple<>();
        for (final String feature : features) {
            if (feature.startsWith("t")) {
                ports.first = Util.parseInt(feature.substring(1));
            }
            else if (feature.startsWith("s")) {
                ports.second = Util.parseInt(feature.substring(1));
            }
        }
        return ports;
    }

    protected static List<String> excludePortFeatures(final List<String> features) {
        final ImmutableListBuilder<String> listBuilder = new ImmutableListBuilder<>();
        for (final String feature : features) {
            if (feature.startsWith("t") || feature.startsWith("s")) { continue; }
            listBuilder.add(feature);
        }
        return listBuilder.build();
    }

    public ElectrumPeer(final Ip ip, final String host, final List<String> features) {
        this.ip = ip;
        this.host = host;
        this.otherFeatures = ElectrumPeer.excludePortFeatures(features.asConst());

        final Tuple<Integer, Integer> ports = ElectrumPeer.parsePorts(features);
        this.tcpPort = ports.first;
        this.tlsPort = ports.second;
    }

    public ElectrumPeer(final Ip ip, final String host, final String protocolVersion, final Integer port, final Integer tlsPort) {
        this.ip = ip;
        this.host = host;
        this.tcpPort = port;
        this.tlsPort = tlsPort;

        final ImmutableListBuilder<String> features = new ImmutableListBuilder<>();
        if (protocolVersion != null) {
            features.add("v" + protocolVersion);
        }
        this.otherFeatures = features.build();
    }

    @Override
    public Json toJson() {
        final Json json = new ElectrumJson(true);
        json.add(this.ip);
        json.add(this.host);

        final Json featuresJson = new ElectrumJson(true);
        for (final String feature : this.otherFeatures) {
            featuresJson.add(feature);
        }
        if (this.tcpPort != null) {
            featuresJson.add("t" + this.tcpPort);
        }
        if (this.tlsPort != null) {
            featuresJson.add("s" + this.tlsPort);
        }
        json.add(featuresJson);

        return json;
    }
}
