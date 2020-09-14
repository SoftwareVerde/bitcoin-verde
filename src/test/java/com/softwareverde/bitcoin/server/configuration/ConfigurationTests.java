package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.util.Util;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.Properties;

public class ConfigurationTests {
    protected static void stringArrayEquals(final String[] expected, final String[] value) {
        final String errorMessage = "Expected " + expected.length + "[" + (String.join(",", expected) + "], but received " + value.length + "[" + String.join(",", value) + "]");

        if (! Util.areEqual(expected.length, value.length)) {
            Assert.fail(errorMessage);
        }

        for (int i = 0; i < expected.length; ++i) {
            if (! Util.areEqual(expected[i], value[i])) {
                Assert.fail(errorMessage);
            }
        }
    }

    @Test
    public void should_parse_property_that_is_an_array() throws Exception {
        final File tempFile = File.createTempFile("tmp", ".conf");
        tempFile.deleteOnExit();

        try (final FileWriter fileWriter = new FileWriter(tempFile)) {
            fileWriter.append("empty = \n");
            fileWriter.append("empty_array = []\n");
            fileWriter.append("single_value = value\n");
            fileWriter.append("value_with_bracket = va[ue\n");
            fileWriter.append("quoted_value_with_bracket = \"va[ue\"\n");
            fileWriter.append("empty_comma = ,\n");
            fileWriter.append("empty_two_commas = ,,\n");
            fileWriter.append("single_value_with_spaces = single value with spaces\n");
            fileWriter.append("two_values_without_brackets = value one, value two,\n");
            fileWriter.append("two_values_with_brackets = [value one, value two]\n");
            fileWriter.append("two_values_with_quotes = [\"value one\", \"value two\"]\n");
            fileWriter.append("value_with_brackets = [value one]\n");
            fileWriter.append("value_with_bracket_as_value = [\"value one]\"]\n");
            fileWriter.append("some_missing_values_with_brackets = [1, 3, 4, , 6]\n");
            fileWriter.flush();
        }

        final Properties properties = new Properties();
        try (final InputStream inputStream = new FileInputStream(tempFile)) {
            properties.load(inputStream);
        }

        stringArrayEquals(new String[]{ },                              PropertiesUtil.parseStringArrayProperty("empty", null, properties));
        stringArrayEquals(new String[]{ },                              PropertiesUtil.parseStringArrayProperty("empty_array", null, properties));
        stringArrayEquals(new String[]{ "value" },                      PropertiesUtil.parseStringArrayProperty("single_value", null, properties));
        stringArrayEquals(new String[]{ },                              PropertiesUtil.parseStringArrayProperty("value_with_bracket", null, properties));
        stringArrayEquals(new String[]{ "va[ue" },                      PropertiesUtil.parseStringArrayProperty("quoted_value_with_bracket", null, properties));
        stringArrayEquals(new String[]{ "" },                           PropertiesUtil.parseStringArrayProperty("empty_comma", null, properties));
        stringArrayEquals(new String[]{ "", "" },                       PropertiesUtil.parseStringArrayProperty("empty_two_commas", null, properties));
        stringArrayEquals(new String[]{ "single value with spaces" },   PropertiesUtil.parseStringArrayProperty("single_value_with_spaces", null, properties));
        stringArrayEquals(new String[]{ "value one", "value two" },     PropertiesUtil.parseStringArrayProperty("two_values_without_brackets", null, properties));
        stringArrayEquals(new String[]{ "value one", "value two" },     PropertiesUtil.parseStringArrayProperty("two_values_with_brackets", null, properties));
        stringArrayEquals(new String[]{ "value one", "value two" },     PropertiesUtil.parseStringArrayProperty("two_values_with_quotes", null, properties));
        stringArrayEquals(new String[]{ "value one" },                  PropertiesUtil.parseStringArrayProperty("value_with_brackets", null, properties));
        stringArrayEquals(new String[]{ "value one]" },                 PropertiesUtil.parseStringArrayProperty("value_with_bracket_as_value", null, properties));
        stringArrayEquals(new String[]{ "1", "3", "4", "", "6" },       PropertiesUtil.parseStringArrayProperty("some_missing_values_with_brackets", null, properties));
    }
}
