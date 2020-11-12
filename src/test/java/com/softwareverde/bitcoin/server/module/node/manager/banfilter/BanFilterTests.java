package com.softwareverde.bitcoin.server.module.node.manager.banfilter;

import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.node.BitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.test.fake.FakeBitcoinNode;
import com.softwareverde.bitcoin.test.fake.database.FakeBitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.test.fake.database.FakeDatabaseManager;
import com.softwareverde.bitcoin.test.fake.database.FakeDatabaseManagerFactory;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.util.Container;
import com.softwareverde.util.Tuple;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.regex.Pattern;

public class BanFilterTests extends UnitTest {
    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_ban_node_matching_user_agent_blacklist() throws Exception {
        // Setup
        final String userAgent = "/Bitcoin SV:1.0.3/";
        final Pattern pattern = Pattern.compile(".*Bitcoin SV.*");


        final Tuple<Ip, Boolean> wasBanned = new Tuple<>();
        final Container<Boolean> wasDisconnected = new Container<>(false);

        final BanFilterCore banFilter = new BanFilterCore(new FakeDatabaseManagerFactory() {
            @Override
            public DatabaseManager newDatabaseManager() {
                return new FakeDatabaseManager() {
                    @Override
                    public BitcoinNodeDatabaseManager getNodeDatabaseManager() {
                        return new FakeBitcoinNodeDatabaseManager() {
                            @Override
                            public void setIsBanned(final Ip ip, final Boolean isBanned) {
                                wasBanned.first = ip;
                                wasBanned.second = isBanned;
                            }
                        };
                    }
                };
            }
        });

        banFilter.addToUserAgentBlacklist(pattern);

        final FakeBitcoinNode fakeBitcoinNode = new FakeBitcoinNode("1.2.3.4", 8333, null, null) {
            @Override
            public void disconnect() {
                wasDisconnected.value = true;
            }

            @Override
            public String getUserAgent() {
                return userAgent;
            }
        };

        // Action
        banFilter.onNodeConnected(fakeBitcoinNode.getIp());
        banFilter.onNodeHandshakeComplete(fakeBitcoinNode);

        // Assert
        Assert.assertTrue(wasBanned.second);
        Assert.assertTrue(wasDisconnected.value);
    }
}
