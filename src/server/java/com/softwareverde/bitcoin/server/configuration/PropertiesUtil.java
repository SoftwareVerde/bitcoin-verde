package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

import java.util.Properties;

public class PropertiesUtil {
    public static List<String> parseStringArrayProperty(final String propertyName, final String defaultValue, final Properties properties) {
        final String jsonString;
        {
            final String inputString = properties.getProperty(propertyName, defaultValue)
                .replaceAll("^[\\s\\[]*", "")
                .replaceAll("[\\s\\[]*$", "");

            jsonString = "[" + inputString + "]";
        }

        if (! Json.isJson(jsonString)) {
            Logger.warn("Invalid property value for " + propertyName + ": " + jsonString);
            return new MutableList<String>(0);
        }

        final Json stringArrayJson = Json.parse(jsonString);
        if (! stringArrayJson.isArray()) { return new MutableList<String>(0); }

        final int itemCount = stringArrayJson.length();
        final MutableList<String> strings = new MutableList<String>(itemCount);
        for (int i = 0; i < itemCount; ++i) {
            final String string = stringArrayJson.getString(i);
            strings.add(string);
        }
        return strings;
    }

    // TODO: Use PropertiesUtil::parseStringArrayProperty ??
    public static List<NodeProperties> parseSeedNodeProperties(final String propertyName, final Integer defaultNetworkPort, final String defaultValue, final Properties properties) {
        final String propertyStringValue = properties.getProperty(propertyName, defaultValue);
        if (propertyStringValue == null) { return null; }

        final Json seedNodesJson = Json.parse(propertyStringValue);
        final MutableList<NodeProperties> nodePropertiesList = new MutableList<NodeProperties>(seedNodesJson.length());
        for (int i = 0; i < seedNodesJson.length(); ++i) {
            final String propertiesString = seedNodesJson.getString(i);

            final NodeProperties nodeProperties;
            final int indexOfColon = propertiesString.indexOf(":");
            if (indexOfColon < 0) {
                nodeProperties = new NodeProperties(propertiesString, defaultNetworkPort);
            }
            else {
                final String address = propertiesString.substring(0, indexOfColon);
                final Integer port = Util.parseInt(propertiesString.substring(indexOfColon + 1));
                nodeProperties = new NodeProperties(address, port);
            }

            nodePropertiesList.add(nodeProperties);
        }
        return nodePropertiesList;
    }

    protected PropertiesUtil() { }
}
