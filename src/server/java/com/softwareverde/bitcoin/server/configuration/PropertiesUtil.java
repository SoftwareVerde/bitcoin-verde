package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.json.Json;
import com.softwareverde.util.Util;

import java.util.Properties;

public class PropertiesUtil {
    public static String[] parseStringArrayProperty(final String propertyName, final String defaultValue, final Properties properties) {
        final Json stringArrayJson = Json.parse(properties.getProperty(propertyName, defaultValue));
        if (! stringArrayJson.isArray()) { return new String[0]; }

        final int itemCount = stringArrayJson.length();
        final String[] strings = new String[itemCount];
        for (int i = 0; i < itemCount; ++i) {
            final String string = stringArrayJson.getString(i);
            strings[i] = string;
        }
        return strings;
    }

    public static NodeProperties[] parseSeedNodeProperties(final String propertyName, final String defaultValue, final Properties properties) {
        final Json seedNodesJson = Json.parse(properties.getProperty(propertyName, defaultValue));
        final NodeProperties[] nodePropertiesArray = new NodeProperties[seedNodesJson.length()];
        for (int i = 0; i < seedNodesJson.length(); ++i) {
            final String propertiesString = seedNodesJson.getString(i);

            final NodeProperties nodeProperties;
            final int indexOfColon = propertiesString.indexOf(":");
            if (indexOfColon < 0) {
                nodeProperties = new NodeProperties(propertiesString, BitcoinConstants.getDefaultNetworkPort());
            }
            else {
                final String address = propertiesString.substring(0, indexOfColon);
                final Integer port = Util.parseInt(propertiesString.substring(indexOfColon + 1));
                nodeProperties = new NodeProperties(address, port);
            }

            nodePropertiesArray[i] = nodeProperties;
        }
        return nodePropertiesArray;
    }

    protected PropertiesUtil() { }
}
