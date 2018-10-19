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
        cache* _master_cache;

    public:
        cache() : _master_cache(0) { }

        void cache_utxo(const prevout* const prevout, const jlong transaction_output_id) {
            _map[prevout] = transaction_output_id;
            _reverse_map[transaction_output_id] = prevout;
        }

        ~cache() {
            for (cache_t::iterator iterator = _map.begin(); iterator != _map.end(); iterator++) {
                delete iterator->first;
            }
        }

};

cache** CACHES = 0;

JNIEXPORT void JNICALL Java_com_softwareverde_bitcoin_jni_bin_NativeUnspentTransactionOutputCache__1init(JNIEnv* enviconment, jclass _class) {
    CACHES = new cache*[256];
    memset(CACHES, 0, 256 * sizeof(cache*));
}

JNIEXPORT void JNICALL Java_com_softwareverde_bitcoin_jni_bin_NativeUnspentTransactionOutputCache__1destroy(JNIEnv *, jclass) {
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

    cache* cache = CACHES[cache_index];
    if (cache == 0) { return; }

    printf("Caching: ");
    for (int i = 0; i < length; ++i) {
        printf("%02X", transaction_hash[i]);
    }
    printf(":%d -> %ld\n", transaction_output_index, transaction_output_id);

    const prevout* prevout = new struct prevout(transaction_hash, transaction_output_index);
    cache->cache_utxo(prevout, transaction_output_id);
}
