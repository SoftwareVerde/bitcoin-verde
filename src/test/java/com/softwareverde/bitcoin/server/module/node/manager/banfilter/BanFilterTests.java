package com.softwareverde.bitcoin.server.module.node.manager.banfilter;

import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeStore;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeStoreCore;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.test.fake.FakeBitcoinNode;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.util.Container;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;
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

        final BitcoinNodeStore bitcoinNodeStore = new BitcoinNodeStoreCore(){
            @Override
            public void banIp(final Ip ip) {
                super.banIp(ip);

                wasBanned.first = ip;
                wasBanned.second = true;
            }

            @Override
            public void unbanIp(final Ip ip) {
                super.unbanIp(ip);

                if (Util.areEqual(wasBanned.first, ip)) {
                    wasBanned.second = false;
                }
            }
        };

        final BanFilterCore banFilter = new BanFilterCore(bitcoinNodeStore);

        banFilter.addToUserAgentBlacklist(pattern);

        final FakeBitcoinNode fakeBitcoinNode = new FakeBitcoinNode("1.2.3.4", 8333, null) {
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
