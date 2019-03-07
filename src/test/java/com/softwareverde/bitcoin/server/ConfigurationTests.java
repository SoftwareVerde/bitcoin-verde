package com.softwareverde.bitcoin.server;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;

public class ConfigurationTests {
    protected static void stringArrayEquals(final String[] expected, final String[] value) {
        Assert.assertEquals(expected.length, value.length);
        for (int i = 0; i < expected.length; ++i) {
            Assert.assertEquals(expected[i], value[i]);
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
            fileWriter.append("empty_comma = ,\n");
            fileWriter.append("empty_two_commas = ,,\n");
            fileWriter.append("single_value_with_spaces = single value with spaces\n");
            fileWriter.append("two_values = value one, value two\n");
            fileWriter.append("two_values_with_brackets = [value one, value two]\n");
            fileWriter.append("value_with_brackets = [value one]\n");
            fileWriter.append("some_missing_values_with_brackets = [1, 3, 4, , 6]\n");
            fileWriter.flush();
        }

        final Configuration configuration = new Configuration(tempFile);
        stringArrayEquals(new String[]{ },                              configuration._getArrayStringProperty("empty"));
        stringArrayEquals(new String[]{ },                              configuration._getArrayStringProperty("empty_array"));
        stringArrayEquals(new String[]{ "value" },                      configuration._getArrayStringProperty("single_value"));
        stringArrayEquals(new String[]{ "va[ue" },                      configuration._getArrayStringProperty("value_with_bracket"));
        stringArrayEquals(new String[]{ "" },                           configuration._getArrayStringProperty("empty_comma"));
        stringArrayEquals(new String[]{ "", "" },                       configuration._getArrayStringProperty("empty_two_commas"));
        stringArrayEquals(new String[]{ "single value with spaces" },   configuration._getArrayStringProperty("single_value_with_spaces"));
        stringArrayEquals(new String[]{ "value one", "value two" },     configuration._getArrayStringProperty("two_values"));
        stringArrayEquals(new String[]{ "value one", "value two" },     configuration._getArrayStringProperty("two_values_with_brackets"));
        stringArrayEquals(new String[]{ "value one" },                  configuration._getArrayStringProperty("value_with_brackets"));
        stringArrayEquals(new String[]{ "1", "3", "4", "", "6" },       configuration._getArrayStringProperty("some_missing_values_with_brackets"));
    }
}
