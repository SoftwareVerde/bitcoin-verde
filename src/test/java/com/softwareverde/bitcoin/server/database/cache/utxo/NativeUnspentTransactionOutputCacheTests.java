package com.softwareverde.bitcoin.server.database.cache.utxo;

import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.type.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import org.junit.Assert;
import org.junit.Test;

public class NativeUnspentTransactionOutputCacheTests {

    protected Sha256Hash sha256(final Long value) {
        return MutableSha256Hash.wrap(BitcoinUtil.sha256(ByteUtil.longToBytes(value)));
    }

    @Test
    public void should_store_and_purge_items_from_the_native_utxo_cache() {
        NativeUnspentTransactionOutputCache.init();

        Assert.assertTrue(NativeUnspentTransactionOutputCache.isEnabled());

        final NativeUnspentTransactionOutputCache cache = new NativeUnspentTransactionOutputCache(UtxoCount.wrap(10L));

        cache.cacheUnspentTransactionOutputId(sha256(0L), 0, TransactionOutputId.wrap(1L));
        cache.cacheUnspentTransactionOutputId(sha256(1L), 0, TransactionOutputId.wrap(2L));
        cache.cacheUnspentTransactionOutputId(sha256(2L), 0, TransactionOutputId.wrap(3L));
        cache.cacheUnspentTransactionOutputId(sha256(3L), 0, TransactionOutputId.wrap(4L));
        cache.cacheUnspentTransactionOutputId(sha256(4L), 0, TransactionOutputId.wrap(5L));
        cache.cacheUnspentTransactionOutputId(sha256(5L), 0, TransactionOutputId.wrap(6L));
        cache.cacheUnspentTransactionOutputId(sha256(6L), 0, TransactionOutputId.wrap(7L));
        cache.cacheUnspentTransactionOutputId(sha256(7L), 0, TransactionOutputId.wrap(8L));
        cache.cacheUnspentTransactionOutputId(sha256(8L), 0, TransactionOutputId.wrap(9L));
        cache.cacheUnspentTransactionOutputId(sha256(9L), 0, TransactionOutputId.wrap(10L));

        Assert.assertEquals(TransactionOutputId.wrap(1L), cache.getCachedUnspentTransactionOutputId(sha256(0L), 0));

        cache.cacheUnspentTransactionOutputId(sha256(10L), 0, TransactionOutputId.wrap(11L));
        Assert.assertNull(cache.getCachedUnspentTransactionOutputId(sha256(0L), 0));

        Assert.assertEquals(TransactionOutputId.wrap(2L), cache.getCachedUnspentTransactionOutputId(sha256(1L), 0));
        Assert.assertEquals(TransactionOutputId.wrap(3L), cache.getCachedUnspentTransactionOutputId(sha256(2L), 0));
        Assert.assertEquals(TransactionOutputId.wrap(4L), cache.getCachedUnspentTransactionOutputId(sha256(3L), 0));
        Assert.assertEquals(TransactionOutputId.wrap(5L), cache.getCachedUnspentTransactionOutputId(sha256(4L), 0));
        Assert.assertEquals(TransactionOutputId.wrap(6L), cache.getCachedUnspentTransactionOutputId(sha256(5L), 0));
        Assert.assertEquals(TransactionOutputId.wrap(7L), cache.getCachedUnspentTransactionOutputId(sha256(6L), 0));
        Assert.assertEquals(TransactionOutputId.wrap(8L), cache.getCachedUnspentTransactionOutputId(sha256(7L), 0));
        Assert.assertEquals(TransactionOutputId.wrap(9L), cache.getCachedUnspentTransactionOutputId(sha256(8L), 0));
        Assert.assertEquals(TransactionOutputId.wrap(10L), cache.getCachedUnspentTransactionOutputId(sha256(9L), 0));
        Assert.assertEquals(TransactionOutputId.wrap(11L), cache.getCachedUnspentTransactionOutputId(sha256(10L), 0));

        NativeUnspentTransactionOutputCache.destroy();
    }
}
