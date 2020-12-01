package com.softwareverde.com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.test.IntegrationTest;
import org.junit.Test;

public class BlockProcessorTests extends IntegrationTest {
    @Override
    public void before() throws Exception {
        super.before();
    }

    @Override
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_handle_replicated_delayed_deep_reorg_with_synced_headers_with_semi_real_blocks() {
        // TODO: This test should attempt to setup the scenario encountered on 2020-11-29 where the node fell behind
        //  syncing, during which time a reorg occurred having the node's headBlock on a different blockchain then the
        //  head blockHeader with multiple blocks (and headers) available after the reorg (2+).
        //  BlockHeight: 663701
    }
}
