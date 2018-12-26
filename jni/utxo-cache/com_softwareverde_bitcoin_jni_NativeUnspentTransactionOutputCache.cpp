#include <jni.h>
#include <map>
#include <list>
#include <cstring>
#include <atomic>
#include "com_softwareverde_bitcoin_jni_NativeUnspentTransactionOutputCache.h"
#include "cpp-btree-1.0.1/btree_map.h"
#include "cpp-btree-1.0.1/btree_set.h"

std::atomic<unsigned long> PREVOUT_ID(0L);

template<typename T>
void atomic_set_max(std::atomic<T>& maximum_value, T const& value) {
    T prev_value = maximum_value;
    while( (prev_value < value) && (! maximum_value.compare_exchange_weak(prev_value, value)) );
}

struct prevout {
    jbyte transaction_hash[32];
    jint transaction_output_index;
    unsigned long insert_id;

    prevout(const unsigned long insert_id, const jbyte* transaction_hash, const jint transaction_output_index) {
        atomic_set_max(PREVOUT_ID, insert_id + 1); // Gaurantee that the next PREVOUT_ID is greater than the insert_id...

        memcpy(this->transaction_hash, transaction_hash, 32 * sizeof(jbyte));
        this->transaction_output_index = transaction_output_index;
        this->insert_id = insert_id;
    }

    prevout(const jbyte* transaction_hash, const jint transaction_output_index) {
        memcpy(this->transaction_hash, transaction_hash, 32 * sizeof(jbyte));
        this->transaction_output_index = transaction_output_index;
        this->insert_id = PREVOUT_ID++;
    }

    prevout(const prevout& prevout) {
        memcpy(this->transaction_hash, prevout.transaction_hash, 32 * sizeof(jbyte));
        this->transaction_output_index = prevout.transaction_output_index;
        this->insert_id = PREVOUT_ID++;
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

struct prevout_id_comparator {
    bool operator()(const prevout* prevout0, const prevout* prevout1) const {
        return (prevout0->insert_id < prevout1->insert_id);
    }
};

#define btree_node_size 256
#define allocator_t std::allocator< std::pair<const prevout*, jlong> >
typedef btree::btree_map<const prevout*, jlong, prevout_ptr_comparator, allocator_t, btree_node_size> cache_t;
typedef btree::btree_set<const prevout*, prevout_id_comparator, allocator_t, btree_node_size> cache_age_t;

typedef btree::btree_set<const prevout*> list_t;

class cache {
    public:
        cache_t _map;
        cache_age_t _map_age;
        list_t _invalidated_items;
        const cache* _master_cache;
        unsigned long _max_item_count;

        void _ensure_capacity(const unsigned long max_item_count) {
            if (max_item_count < 1) { return; }

            while (_map.size() >= max_item_count) {
                const cache_age_t::iterator oldest_prevout_iterator = _map_age.begin();
                const prevout* const oldest_prevout = (*oldest_prevout_iterator);
                _map.erase(oldest_prevout);
                _map_age.erase(oldest_prevout_iterator);
                delete oldest_prevout;
            }
        }

    public:
        cache() : _master_cache(0), _max_item_count(2147483647) { }

        void set_max_item_count(const unsigned long item_count) {
            _max_item_count = item_count;
        }

        void cache_utxo(const prevout* const prevout, const jlong transaction_output_id) {
            _ensure_capacity(_max_item_count);

            const cache_t::iterator iterator = _map.find(prevout);
            if (iterator != _map.end()) {
                iterator->second = transaction_output_id;
                delete prevout;
            }
            else {
                _map[prevout] = transaction_output_id;
                _map_age.insert(prevout);
            }
        }

        jlong get_cached_utxo(const prevout& prevout) const {
            const cache_t::const_iterator iterator = _map.find(&prevout);
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

        void invalidate_utxo(const prevout* const prevout) {
            // Invalidating the UTXO just adds it to the invalidation queue; the item is not immediately removed until a commit takes place.
            _invalidated_items.insert(prevout);
        }

        void commit(cache* const cache) {
            for (list_t::iterator list_iterator = cache->_invalidated_items.begin(); list_iterator != cache->_invalidated_items.end(); list_iterator++) {
                const prevout* const invalidated_prevout = *list_iterator;
                const cache_t::iterator map_iterator = _map.find(invalidated_prevout);
                if (map_iterator != _map.end()) {
                    const prevout* const map_prevout = map_iterator->first;
                    _map.erase(map_iterator);
                    _map_age.erase(map_prevout);
                    delete map_prevout;
                }
                delete invalidated_prevout;
            }

            for (cache_t::iterator cache_iterator = cache->_map.begin(); cache_iterator != cache->_map.end(); cache_iterator++) {
                const prevout* const prevout = cache_iterator->first;
                const jlong transaction_output_id = cache_iterator->second;

                const cache_t::iterator map_iterator = _map.find(prevout);
                if (map_iterator != _map.end()) {
                    map_iterator->second = transaction_output_id;
                    delete prevout;
                }
                else {
                    _ensure_capacity(_max_item_count);
                    _map[prevout] = transaction_output_id;
                    _map_age.insert(prevout);
                }
            }

            cache->_map.clear();
            cache->_invalidated_items.clear();
        }

        void commit() {
            for (list_t::iterator iterator = _invalidated_items.begin(); iterator != _invalidated_items.end(); iterator++) {
                const prevout* const prevout = (*iterator);
                _map.erase(prevout);
                _map_age.erase(prevout);
                delete prevout;
            }
            _invalidated_items.clear();
        }

        void prune_half() {
            _ensure_capacity(_map.size() / 2);
        }

        ~cache() {
            for (cache_t::iterator iterator = _map.begin(); iterator != _map.end(); iterator++) {
                delete iterator->first;
            }

            for (list_t::iterator iterator = _invalidated_items.begin(); iterator != _invalidated_items.end(); iterator++) {
                delete (*iterator);
            }
        }

};

cache** CACHES = 0;

JNIEXPORT void JNICALL Java_com_softwareverde_bitcoin_jni_NativeUnspentTransactionOutputCache__1init(JNIEnv* environment, jclass _class) {
    CACHES = new cache*[256];
    memset(CACHES, 0, 256 * sizeof(cache*));
}

JNIEXPORT void JNICALL Java_com_softwareverde_bitcoin_jni_NativeUnspentTransactionOutputCache__1destroy(JNIEnv* environment, jclass _class) {
    for (int i=0; i<256; ++i) {
        if (CACHES[i] != 0) {
            delete CACHES[i];
            CACHES[i] = 0;
        }
    }
    delete[] CACHES;
}

JNIEXPORT jint JNICALL Java_com_softwareverde_bitcoin_jni_NativeUnspentTransactionOutputCache__1createCache(JNIEnv* environment, jclass _class) {
    for (int i=0; i<256; ++i) {
        if (CACHES[i] == 0) {
            CACHES[i] = new cache();
            return i;
        }
    }

    return -1;
}

JNIEXPORT void JNICALL Java_com_softwareverde_bitcoin_jni_NativeUnspentTransactionOutputCache__1deleteCache(JNIEnv* environment, jclass _class, jint cache_index) {
    if (cache_index >= 256) { return; }
    if (cache_index < 0) { return; }

    if (CACHES[cache_index] != 0) {
        delete CACHES[cache_index];
        CACHES[cache_index] = 0;
    }
}

JNIEXPORT void JNICALL Java_com_softwareverde_bitcoin_jni_NativeUnspentTransactionOutputCache__1cacheUnspentTransactionOutputId(JNIEnv* environment, jclass _class, jint cache_index, jbyteArray jni_transaction_hash, jint transaction_output_index, jlong transaction_output_id) {
    if (cache_index >= 256) { return; }
    if (cache_index < 0) { return; }

    cache* const cache = CACHES[cache_index];
    if (cache == 0) { return; }

    jbyte* transaction_hash = environment->GetByteArrayElements(jni_transaction_hash, NULL);
    const jsize length = 32; // environment->GetArrayLength(environment, jni_transaction_hash);

    const prevout* const prevout = new struct prevout(transaction_hash, transaction_output_index);
    cache->cache_utxo(prevout, transaction_output_id);

    environment->ReleaseByteArrayElements(jni_transaction_hash, transaction_hash, 0);
}

JNIEXPORT jlong JNICALL Java_com_softwareverde_bitcoin_jni_NativeUnspentTransactionOutputCache__1getCachedUnspentTransactionOutputId(JNIEnv* environment, jclass _class, jint cache_index, jbyteArray jni_transaction_hash, jint transaction_output_index) {
    if (cache_index >= 256) { return -1; }
    if (cache_index < 0) { return -1; }

    const cache* const cache = CACHES[cache_index];
    if (cache == 0) { return -1; }

    jbyte* transaction_hash = environment->GetByteArrayElements(jni_transaction_hash, NULL);
    const jsize length = 32; // environment->GetArrayLength(environment, jni_transaction_hash);

    const prevout prevout(transaction_hash, transaction_output_index);
    const jlong transaction_output_id = cache->get_cached_utxo(prevout);

    environment->ReleaseByteArrayElements(jni_transaction_hash, transaction_hash, 0);

    return transaction_output_id;
}

JNIEXPORT void JNICALL Java_com_softwareverde_bitcoin_jni_NativeUnspentTransactionOutputCache__1setMasterCache(JNIEnv* environment, jclass _class, jint cache_index, jint master_cache_index) {
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

JNIEXPORT void JNICALL Java_com_softwareverde_bitcoin_jni_NativeUnspentTransactionOutputCache__1invalidateUnspentTransactionOutputId(JNIEnv* environment, jclass _class, jint cache_index, jbyteArray jni_transaction_hash, jint transaction_output_index) {
    if (cache_index >= 256) { return; }
    if (cache_index < 0) { return; }

    cache* const cache = CACHES[cache_index];
    if (cache == 0) { return; }

    jbyte* transaction_hash = environment->GetByteArrayElements(jni_transaction_hash, NULL);
    const jsize length = 32; // environment->GetArrayLength(environment, jni_transaction_hash);

    const prevout* const prevout = new struct prevout(transaction_hash, transaction_output_index);
    cache->invalidate_utxo(prevout);

    environment->ReleaseByteArrayElements(jni_transaction_hash, transaction_hash, 0);
}

JNIEXPORT void JNICALL Java_com_softwareverde_bitcoin_jni_NativeUnspentTransactionOutputCache__1commit__II(JNIEnv* environment, jclass _class, jint commit_to_cache_index, jint cache_index) {
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

JNIEXPORT void JNICALL Java_com_softwareverde_bitcoin_jni_NativeUnspentTransactionOutputCache__1commit__I(JNIEnv* environment, jclass _class, jint cache_index) {
    if (cache_index >= 256) { return; }
    if (cache_index < 0) { return; }

    cache* const cache = CACHES[cache_index];
    if (cache == 0) { return; }

    cache->commit();
}

JNIEXPORT void JNICALL Java_com_softwareverde_bitcoin_jni_NativeUnspentTransactionOutputCache__1setMaxItemCount(JNIEnv* environment, jclass _class, jint cache_index, jlong max_item_count) {
    if (cache_index >= 256) { return; }
    if (cache_index < 0) { return; }

    cache* const cache = CACHES[cache_index];
    if (cache == 0) { return; }
    
    cache->set_max_item_count(max_item_count);
}

JNIEXPORT void JNICALL Java_com_softwareverde_bitcoin_jni_NativeUnspentTransactionOutputCache__1loadUnspentTransactionOutputId(JNIEnv* environment, jclass _class, jint cache_index, jlong insert_id, jbyteArray jni_transaction_hash, jint transaction_output_index, jlong transaction_output_id) {
    if (cache_index >= 256) { return; }
    if (cache_index < 0) { return; }

    cache* const cache = CACHES[cache_index];
    if (cache == 0) { return; }

    jbyte* transaction_hash = environment->GetByteArrayElements(jni_transaction_hash, NULL);
    const jsize length = 32; // environment->GetArrayLength(environment, jni_transaction_hash);

    const prevout* const prevout = new struct prevout(insert_id, transaction_hash, transaction_output_index);
    cache->cache_utxo(prevout, transaction_output_id);

    environment->ReleaseByteArrayElements(jni_transaction_hash, transaction_hash, 0);
}

JNIEXPORT void JNICALL Java_com_softwareverde_bitcoin_jni_NativeUnspentTransactionOutputCache__1pruneHalf(JNIEnv* environment, jclass _class, jint cache_index) {
    if (cache_index >= 256) { return; }
    if (cache_index < 0) { return; }

    cache* const cache = CACHES[cache_index];
    if (cache == 0) { return; }
    
    cache->prune_half();
}
