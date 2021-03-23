package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.server.configuration.Configuration;
import com.softwareverde.bitcoin.server.configuration.ConfigurationPropertiesExporter;
import com.softwareverde.bitcoin.util.BitcoinUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class ConfigurationModule {
    final String _configurationFilename;
    final Configuration _configuration;
    final Map<String, String> _userInputMap = new HashMap<>();
    final BufferedReader _bufferedReader = new BufferedReader(new InputStreamReader(System.in));

    public ConfigurationModule(final String configurationFilename, final boolean editExistingConfiguration) {
        _configurationFilename = configurationFilename;

        if (editExistingConfiguration) {
            _configuration = new Configuration(new File(configurationFilename));
            return;
        }

        _configuration = new Configuration();
    }

    // TODO: implement edit existing config, since default values won't even allow the node to run
    public void run() {
        System.out.println("Starting node configuration.");
        System.out.println("Please specify the following parameters. Select default parameters by returning an empty value.\n");

        try {
            final String maxMemoryByteCount = "bitcoin." + ConfigurationPropertiesExporter.MAX_MEMORY_BYTE_COUNT;
            System.out.printf("[%s]%n", maxMemoryByteCount);
            System.out.println("The maximum byte count for the InnoDB buffer pool. The buffer pool size is one of the most important configurations for performance as it configures the space used to store/cache table indexes.");
            _readUserInput(maxMemoryByteCount);

            System.out.printf("[%s]%n", ConfigurationPropertiesExporter.BAN_FILTER_IS_ENABLED);
            System.out.println("If set to zero or false, then nodes will not be banned under any circumstances. Additionally, any previously banned nodes will be unbanned while disabled.");
            _readUserInput(ConfigurationPropertiesExporter.BAN_FILTER_IS_ENABLED, true);

            System.out.printf("[%s]%n", ConfigurationPropertiesExporter.MAX_PEER_COUNT);
            System.out.println("The maximum number of peers that the node will accept.");
            _readUserInput(ConfigurationPropertiesExporter.MAX_PEER_COUNT);

            System.out.printf("[%s]%n", ConfigurationPropertiesExporter.MAX_THREAD_COUNT);
            System.out.println("The max number of threads used to validate a block. Currently, the server will create max(maxPeerCount * 8, 256) threads for network communication; in the future this property will likely claim this label.");
            _readUserInput(ConfigurationPropertiesExporter.MAX_THREAD_COUNT);
        }
        catch (final Exception exception) {
            System.out.println(exception.toString());
            exception.printStackTrace();
            BitcoinUtil.exitFailure();
        }

        ConfigurationPropertiesExporter.exportConfiguration(_configuration, _configurationFilename, _userInputMap);
        System.out.println("Node configuration is complete.");
    }

    private void _readUserInput(final String propertyKey) throws Exception {
        _readUserInput(propertyKey, false);
    }

    private void _readUserInput(final String propertyKey, final boolean isBoolean) throws Exception {
        if (isBoolean) {
            System.out.println("Enter value (1 or 0):");
        }
        else {
            System.out.println("Enter value:");
        }

        final String userInput = _bufferedReader.readLine();
        _userInputMap.put(propertyKey, userInput);
    }
}
