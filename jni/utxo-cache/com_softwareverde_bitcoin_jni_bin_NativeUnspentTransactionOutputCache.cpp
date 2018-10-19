#include <jni.h>
#include <stdio.h>
#include <map>
#include <list>
#include "com_softwareverde_bitcoin_jni_bin_NativeUnspentTransactionOutputCache.h"

struct prevout {
    jbyte transaction_hash[32];
    jint transaction_output_index;

    prevout(const jbyte* param_transaction_hash, const jint param_transaction_output_index) {
        memcpy(transaction_hash, param_transaction_hash, 32 * sizeof(jbyte));
        transaction_output_index = param_transaction_output_index;
    }

    prevout(const prevout& prevout) {
        memcpy(transaction_hash, prevout.transaction_hash, 32 * sizeof(jbyte));
        transaction_output_index = prevout.transaction_output_index;
    }

    bool operator==(const prevout& prevout) const {
        for (int i=0; i<32; ++i) {
            if (transaction_hash[i] != prevout.transaction_hash[i]) {
                return false;
            }
        }
        return (transaction_output_index < prevout.transaction_output_index);
    }

    bool operator<(const prevout& prevout) const {
        for (int i=0; i<32; ++i) {
            if (transaction_hash[i] != prevout.transaction_hash[i]) {
                return (transaction_hash[i] < prevout.transaction_hash[i]);
            }
        }
        return (transaction_output_index < prevout.transaction_output_index);
    }
};

struct prevout_ptr_comparator {
    bool operator()(const prevout* prevout0, const prevout* prevout1) const {
        return (*prevout0) < (*prevout1);
    }
};

typedef std::map<const prevout*, jlong, prevout_ptr_comparator> cache_t;
typedef std::map<jlong, const prevout*> reverse_cache_t;
typedef std::list<jlong> list_t;

class cache {
    private:
        cache_t _map;
        reverse_cache_t _reverse_map;
        list_t _invalidated_items;
        const cache* _master_cache;

        void _remove_transaction_output_id(const jlong transaction_output_id) {
            const reverse_cache_t::iterator iterator = _reverse_map.find(transaction_output_id);
            if (iterator == _reverse_map.end()) { return; }

            const prevout* prevout = iterator->second;
            _reverse_map.erase(iterator);

            _map.erase(prevout);
        }

    public:
        cache() : _master_cache(0) { }

        void cache_utxo(const prevout* const prevout, const jlong transaction_output_id) {
            _map[prevout] = transaction_output_id;
            _reverse_map[transaction_output_id] = prevout;
        }

        jlong get_cached_utxo(const prevout& prevout) const {
            cache_t::const_iterator iterator = _map.find(&prevout);
            if (iterator != _map.end()) {
                return iterator->second;
            }

            if (_master_cache != 0) {
                return _master_cache->get_cached_utxo(prevout);
            }

            return -1;
        }

        void set_master_cache(const cache* const master_cache) {
            _master_cache = master_cache;
        }

        void invalidate_transaction_output_id(const jlong transaction_output_id) {
            _remove_transaction_output_id(transaction_output_id);
            _invalidated_items.push_back(transaction_output_id);
        }

        void commit(cache* const cache) {
            for (cache_t::iterator iterator = cache->_map.begin(); iterator != cache->_map.end(); iterator++) {
                _map[iterator->first] = iterator->second;
                _reverse_map[iterator->second] = iterator->first;
            }

            for (list_t::iterator iterator = cache->_invalidated_items.begin(); iterator != cache->_invalidated_items.end(); iterator++) {
                _remove_transaction_output_id(*iterator);
            }

            cache->_map.clear();
            cache->_reverse_map.clear();
            cache->_invalidated_items.clear();
        }

        ~cache() {
            for (cache_t::iterator iterator = _map.begin(); iterator != _map.end(); iterator++) {
                delete iterator->first;
            }
        }

};

cache** CACHES = 0;

JNIEXPORT void JNICALL Java_com_softwareverde_bitcoin_jni_bin_NativeUnspentTransactionOutputCache__1init(JNIEnv* environment, jclass _class) {
    CACHES = new cache*[256];
    memset(CACHES, 0, 256 * sizeof(cache*));
}

JNIEXPORT void JNICALL Java_com_softwareverde_bitcoin_jni_bin_NativeUnspentTransactionOutputCache__1destroy(JNIEnv* environment, jclass _class) {
    for (int i=0; i<256; ++i) {
        if (CACHES[i] != 0) {
            delete CACHES[i];
            CACHES[i] = 0;
        }
    }
    delete[] CACHES;
}

JNIEXPORT jint JNICALL Java_com_softwareverde_bitcoin_jni_bin_NativeUnspentTransactionOutputCache__1createCache(JNIEnv* environment, jclass _class) {
    for (int i=0; i<256; ++i) {
        if (CACHES[i] == 0) {
            printf("Allocating cache at index: %d\n", i);
            CACHES[i] = new cache();
            return i;
        }
    }

    return -1;
}

JNIEXPORT void JNICALL Java_com_softwareverde_bitcoin_jni_bin_NativeUnspentTransactionOutputCache__1deleteCache(JNIEnv* environment, jclass _class, jint cache_index) {
    if (cache_index >= 256) { return; }
    if (cache_index < 0) { return; }

    if (CACHES[cache_index] != 0) {
        delete CACHES[cache_index];
        CACHES[cache_index] = 0;
    }
}

JNIEXPORT void JNICALL Java_com_softwareverde_bitcoin_jni_bin_NativeUnspentTransactionOutputCache__1cacheUnspentTransactionOutputId(JNIEnv* environment, jclass _class, jint cache_index, jbyteArray jni_transaction_hash, jint transaction_output_index, jlong transaction_output_id) {
    const jbyte* transaction_hash = environment->GetByteArrayElements(jni_transaction_hash, NULL);
    const jsize length = 32; // environment->GetArrayLength(environment, jni_transaction_hash);

    if (cache_index >= 256) { return; }
    if (cache_index < 0) { return; }

    cache* const cache = CACHES[cache_index];
    if (cache == 0) { return; }

    const prevout* prevout = new struct prevout(transaction_hash, transaction_output_index);
    cache->cache_utxo(prevout, transaction_output_id);
}

JNIEXPORT jlong JNICALL Java_com_softwareverde_bitcoin_jni_bin_NativeUnspentTransactionOutputCache__1getCachedUnspentTransactionOutputId(JNIEnv* environment, jclass _class, jint cache_index, jbyteArray jni_transaction_hash, jint transaction_output_index) {
    const jbyte* transaction_hash = environment->GetByteArrayElements(jni_transaction_hash, NULL);
    const jsize length = 32; // environment->GetArrayLength(environment, jni_transaction_hash);

    if (cache_index >= 256) { return -1; }
    if (cache_index < 0) { return -1; }

    const cache* const cache = CACHES[cache_index];
    if (cache == 0) { return -1; }

    const prevout prevout(transaction_hash, transaction_output_index);
    return cache->get_cached_utxo(prevout);
}

JNIEXPORT void JNICALL Java_com_softwareverde_bitcoin_jni_bin_NativeUnspentTransactionOutputCache__1setMasterCache(JNIEnv* environment, jclass _class, jint cache_index, jint master_cache_index) {
    if (cache_index >= 256) { return; }
    if (cache_index < 0) { return; }

    cache* const cache = CACHES[cache_index];
    if (cache == 0) { return; }

    if (master_cache_index >= 256) { return; }

    if (master_cache_index >= 0) {
        const class cache* const master_cache = CACHES[master_cache_index];
        cache->set_master_cache(master_cache);
    }
    else {
        cache->set_master_cache(0);
    }
}

JNIEXPORT void JNICALL Java_com_softwareverde_bitcoin_jni_bin_NativeUnspentTransactionOutputCache__1invalidateUnspentTransactionOutputId(JNIEnv* environment, jclass _class, jint cache_index, jlong transaction_output_id) {
    if (cache_index >= 256) { return; }
    if (cache_index < 0) { return; }

    cache* const cache = CACHES[cache_index];
    if (cache == 0) { return; }

    cache->invalidate_transaction_output_id(transaction_output_id);
}

JNIEXPORT void JNICALL Java_com_softwareverde_bitcoin_jni_bin_NativeUnspentTransactionOutputCache__1commit(JNIEnv* environment, jclass _class, jint commit_to_cache_index, jint cache_index) {
    if (commit_to_cache_index >= 256) { return; }
    if (commit_to_cache_index < 0) { return; }

    cache* const commit_to_cache = CACHES[commit_to_cache_index];
    if (commit_to_cache == 0) { return; }

    if (cache_index >= 256) { return; }
    if (cache_index < 0) { return; }

    cache* const cache = CACHES[cache_index];
    if (cache == 0) { return; }

    commit_to_cache->commit(cache);
}

