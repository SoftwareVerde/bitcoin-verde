package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.server.configuration.BitcoinProperties;
import com.softwareverde.bitcoin.server.configuration.Configuration;
import com.softwareverde.bitcoin.server.configuration.PropertiesExporter;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.util.IoUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ConfigurationModule {
    final String _configurationFilename;
    final Configuration _configuration;

    public ConfigurationModule(final String configurationFilename, final boolean editExistingConfiguration) {
        _configurationFilename = configurationFilename;

        if (editExistingConfiguration) {
            _configuration = new Configuration(new File(configurationFilename));
            return;
        }

        _configuration = new Configuration();
    }

    public void run() {
        System.out.println("Starting node configuration.");

        final Map<String, String> userInputMap = new HashMap<>();
        final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Please specify the following parameters. Select default parameters by returning an empty value.");

        try {
            System.out.println("The maximum byte count for the InnoDB buffer pool. The buffer pool size is one of the most important configurations for performance as it configures the space used to store/cache table indexes.");
            System.out.println("Max memory byte count: ");
            final String maxMemoryByteCount = bufferedReader.readLine();
            userInputMap.put("bitcoin." + PropertiesExporter.MAX_MEMORY_BYTE_COUNT, maxMemoryByteCount);

            System.out.println("If set to zero or false, then nodes will not be banned under any circumstances. Additionally, any previously banned nodes will be unbanned while disabled.");
            System.out.println("Enable ban filter (1 or 0): ");
            final String enableBanFilter = bufferedReader.readLine();
            userInputMap.put(PropertiesExporter.BAN_FILTER_IS_ENABLED, enableBanFilter);

            final StringBuilder stringBuilder = new StringBuilder();
            final Map<String, String> configurationPropertiesMap = PropertiesExporter.configurationPropertiesToMap(_configuration);
            configurationPropertiesMap.forEach((key, value) -> {
                final String exportedValue = userInputMap.getOrDefault(key, value);
                stringBuilder.append(key).append(" = ").append(exportedValue).append("\n");
            });

            new File(_configurationFilename).delete();
            IoUtil.putFileContents(_configurationFilename, stringBuilder.toString().getBytes(StandardCharsets.UTF_8));
        }
        catch (final Exception exception) {
            System.out.println(exception.toString());
            exception.printStackTrace();
            BitcoinUtil.exitFailure();
        }
    }

    private String _readLineOrDefault(final BufferedReader bufferedReader, final String defaultValue) throws Exception {
        final String line = bufferedReader.readLine();
        if (line == null || line.isEmpty()) {
            return defaultValue;
        }

        return line;
    }
}
