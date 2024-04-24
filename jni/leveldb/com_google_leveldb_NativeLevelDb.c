#include "com_google_leveldb_NativeLevelDb.h"
#include "include/leveldb/c.h"
#include "include/leveldb/db.h"
#include "leveldb/status.h"

struct leveldb_t {
  leveldb::DB* rep;
};

struct leveldb_readoptions_t {
  leveldb::ReadOptions rep;
};

#include <string.h>
#include <stdio.h>
#include <stdlib.h>

JNIEXPORT jlong JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1options_1create
  (JNIEnv* env, jclass object) {
    return (uintptr_t)leveldb_options_create();
}

JNIEXPORT void JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1options_1destroy
  (JNIEnv* env, jclass object, jlong optionsPtr) {
    leveldb_options_destroy(
        (leveldb_options_t*)(uintptr_t)optionsPtr
    );
}


JNIEXPORT void JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1options_1set_1create_1if_1missing
  (JNIEnv* env, jclass object, jlong optionsPtr, jboolean value) {
    leveldb_options_set_create_if_missing(
        (leveldb_options_t*)(uintptr_t)optionsPtr,
        value
    );
}

JNIEXPORT void JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1options_1set_1write_1buffer_1size
  (JNIEnv* env, jclass object, jlong optionsPtr, jlong byteCount) {
    leveldb_options_set_write_buffer_size(
        (leveldb_options_t*)(uintptr_t)optionsPtr,
        (size_t)byteCount
    );
}

JNIEXPORT jlong JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1open
  (JNIEnv* env, jclass object, jlong optionsPtr, jstring dbName) {
    const char *nativeDbName = env->GetStringUTFChars(dbName, 0);
    const leveldb_t* leveldbPtr = leveldb_open(
        (leveldb_options_t*)(uintptr_t)optionsPtr,
        nativeDbName,
        0
    );
    env->ReleaseStringUTFChars(dbName, nativeDbName);
    return (uintptr_t)leveldbPtr;
}

JNIEXPORT void JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1close
  (JNIEnv* env, jclass object, jlong leveldbPtr) {
  leveldb_close((leveldb_t*)(uintptr_t)leveldbPtr);
}

JNIEXPORT jlong JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1writeoptions_1create
  (JNIEnv* env, jclass object) {
    return (uintptr_t)leveldb_writeoptions_create();
}

JNIEXPORT void JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1writeoptions_1destroy
  (JNIEnv* env, jclass object, jlong writeOptionsPtr) {
    leveldb_writeoptions_destroy((leveldb_writeoptions_t*)(uintptr_t)writeOptionsPtr);
}

JNIEXPORT void JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1writeoptions_1set_1sync
  (JNIEnv* env, jclass object, jlong writeOptionsPtr, jboolean value) {
    leveldb_writeoptions_set_sync((leveldb_writeoptions_t*)(uintptr_t)writeOptionsPtr, value);
}

JNIEXPORT void JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1put
  (JNIEnv* env, jclass object, jlong leveldbPtr, jlong writeOptionsPtr, jobject keyBuf, jlong keyByteCount, jobject valueBuf, jlong valueByteCount) {
    const char* keyData = (const char*) env->GetDirectBufferAddress(keyBuf);
    const char* valueData = (const char*) env->GetDirectBufferAddress(valueBuf);

    leveldb_put(
        (leveldb_t*)(uintptr_t)leveldbPtr,
        (leveldb_writeoptions_t*)(uintptr_t)writeOptionsPtr,
        keyData, (size_t)keyByteCount,
        valueData, (size_t)valueByteCount,
        0
    );
}

JNIEXPORT jlong JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1readoptions_1create
  (JNIEnv* env, jclass object) {
    return (uintptr_t)leveldb_readoptions_create();
}

JNIEXPORT void JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1readoptions_1set_1verify_1checksums
  (JNIEnv* env, jclass object, jlong readOptionsPtr, jboolean value) {
    leveldb_readoptions_set_verify_checksums(
        (leveldb_readoptions_t*)readOptionsPtr,
        value
    );
}

JNIEXPORT void JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1readoptions_1set_1fill_1cache
  (JNIEnv* env, jclass object, jlong readOptionsPtr, jboolean value) {
    leveldb_readoptions_set_fill_cache(
        (leveldb_readoptions_t*)readOptionsPtr,
        value
    );
}

JNIEXPORT void JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1readoptions_1destroy
  (JNIEnv* env, jclass object, jlong readOptionsPtr) {
    leveldb_readoptions_destroy((leveldb_readoptions_t*)readOptionsPtr);
}

JNIEXPORT jlong JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1create_1snapshot
  (JNIEnv* env, jclass object, jlong leveldbPtr) {
    return (uintptr_t)leveldb_create_snapshot(
        (leveldb_t*)(uintptr_t)leveldbPtr
    );
}

JNIEXPORT void JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1release_1snapshot
  (JNIEnv* env, jclass object, jlong leveldbPtr, jlong snapshotPtr) {
    leveldb_release_snapshot(
        (leveldb_t*)(uintptr_t)leveldbPtr,
        (leveldb_snapshot_t*)(uintptr_t)snapshotPtr
    );
}

JNIEXPORT void JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1readoptions_1set_1snapshot
  (JNIEnv* env, jclass object, jlong readOptionsPtr, jlong snapshotPtr) {
    leveldb_readoptions_set_snapshot(
        (leveldb_readoptions_t*)readOptionsPtr,
        (leveldb_snapshot_t*)(uintptr_t)snapshotPtr
    );
}

JNIEXPORT jbyteArray JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1get
  (JNIEnv* env, jclass object, jlong leveldbPtr, jlong readOptionsPtr, jobject keyBuf, jlong keyByteCount) {
    const char* keyData = (const char*) env->GetDirectBufferAddress(keyBuf);

    size_t valueByteCount = 0;

    leveldb_t* db = (leveldb_t*)leveldbPtr;
    leveldb_readoptions_t* options = (leveldb_readoptions_t*)readOptionsPtr;

    std::string tmp;
    leveldb::Status s = db->rep->Get(options->rep, leveldb::Slice(keyData, keyByteCount), &tmp);
    if (s.ok()) {
        valueByteCount = tmp.size();
    }
    else {
        valueByteCount = 0;
        if (! s.IsNotFound()) {
            // error
        }
    }

    jbyteArray valueByteArray = env->NewByteArray(valueByteCount);
    if (valueByteCount > 0) {
        env->SetByteArrayRegion(valueByteArray, 0, valueByteCount, (jbyte*)tmp.c_str());
    }

    return valueByteArray;
}

JNIEXPORT void JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1delete
  (JNIEnv* env, jclass object, jlong leveldbPtr, jlong writeOptionsPtr, jobject keyBuf, jlong keyByteCount) {
    const char* keyData = (const char*) env->GetDirectBufferAddress(keyBuf);

    leveldb_delete(
        (leveldb_t*)(uintptr_t)leveldbPtr,
        (leveldb_writeoptions_t*)(uintptr_t)writeOptionsPtr,
        keyData, (size_t)keyByteCount,
        0
    );
}

JNIEXPORT jlong JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1cache_1create_1lru
  (JNIEnv* env, jclass object, jlong byteCount) {
    return (uintptr_t)leveldb_cache_create_lru(
        (size_t)byteCount
    );
}

JNIEXPORT void JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1cache_1destroy
  (JNIEnv* env, jclass object, jlong dbCachePtr) {
    leveldb_cache_destroy(
        (leveldb_cache_t*)(uintptr_t)dbCachePtr
    );
}

JNIEXPORT void JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1options_1set_1cache
  (JNIEnv* env, jclass object, jlong optionsPtr, jlong dbCachePtr) {
    leveldb_options_set_cache(
        (leveldb_options_t*)(uintptr_t)optionsPtr,
        (leveldb_cache_t*)(uintptr_t)dbCachePtr
    );
}

JNIEXPORT jlong JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1writebatch_1create
  (JNIEnv* env, jclass object) {
    return (uintptr_t)leveldb_writebatch_create();
}

JNIEXPORT void JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1writebatch_1destroy
  (JNIEnv* env, jclass object, jlong batchPtr) {
    leveldb_writebatch_destroy(
        (leveldb_writebatch_t*)(uintptr_t)batchPtr
    );
}

JNIEXPORT void JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1writebatch_1clear
  (JNIEnv* env, jclass object, jlong batchPtr) {
    leveldb_writebatch_clear(
        (leveldb_writebatch_t*)(uintptr_t)batchPtr
    );
}

JNIEXPORT void JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1writebatch_1put
  (JNIEnv* env, jclass object, jlong batchPtr, jobject keyBuf, jlong keyByteCount, jobject valueBuf, jlong valueByteCount) {
    const char* keyData = (const char*) env->GetDirectBufferAddress(keyBuf);
    const char* valueData = (const char*) env->GetDirectBufferAddress(valueBuf);

    leveldb_writebatch_put(
        (leveldb_writebatch_t*)(uintptr_t)batchPtr,
        keyData, (size_t)keyByteCount,
        valueData, (size_t)valueByteCount
    );
}

JNIEXPORT void JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1writebatch_1delete
  (JNIEnv* env, jclass object, jlong batchPtr, jobject keyBuf, jlong keyByteCount) {
    const char* keyData = (const char*) env->GetDirectBufferAddress(keyBuf);

    leveldb_writebatch_delete(
        (leveldb_writebatch_t*)(uintptr_t)batchPtr,
        keyData, (size_t)keyByteCount
    );
}

JNIEXPORT void JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1write
  (JNIEnv* env, jclass object, jlong leveldbPtr, jlong writeOptionsPtr, jlong batchPtr) {

    leveldb_write(
        (leveldb_t*)(uintptr_t)leveldbPtr,
        (leveldb_writeoptions_t*)(uintptr_t)writeOptionsPtr,
        (leveldb_writebatch_t*)(uintptr_t)batchPtr,
        0
    );
}

JNIEXPORT jlong JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1filterpolicy_1create_1bloom
  (JNIEnv* env, jclass object, jint bitsPerKey) {
    return (uintptr_t)leveldb_filterpolicy_create_bloom(bitsPerKey);
}

JNIEXPORT void JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1filterpolicy_1destroy
  (JNIEnv* env, jclass object, jlong filterPtr) {
    leveldb_filterpolicy_destroy(
        (leveldb_filterpolicy_t*)(uintptr_t)filterPtr
    );
}

JNIEXPORT void JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1options_1set_1filter_1policy
  (JNIEnv* env, jclass object, jlong optionsPtr, jlong filterPtr) {
    leveldb_options_set_filter_policy(
        (leveldb_options_t*)(uintptr_t)optionsPtr,
        (leveldb_filterpolicy_t*)(uintptr_t)filterPtr
    );
}

JNIEXPORT jboolean JNICALL Java_com_google_leveldb_NativeLevelDb_leveldb_1exists
  (JNIEnv* env, jclass object, jlong leveldbPtr, jlong readOptionsPtr, jobject keyBuf, jlong keyByteCount) {
    const char* keyData = (const char*) env->GetDirectBufferAddress(keyBuf);

    size_t valueByteCount = 0;

    char* value = leveldb_get(
        (leveldb_t*)(uintptr_t)leveldbPtr,
        (leveldb_readoptions_t*)readOptionsPtr,
        keyData, (size_t)keyByteCount,
        &valueByteCount,
        0
    );

    free(value);

    return valueByteCount > 0;
}
