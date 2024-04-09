package com.google.leveldb;

import java.nio.ByteBuffer;

public class NativeLevelDb {

  public final static native long leveldb_options_create();
  public final static native void leveldb_options_destroy(long optionsPointer);
  public final static native void leveldb_options_set_create_if_missing(long optionsPointer, boolean value);
//  public final static native void leveldb_options_set_comparator(long optionsPointer, long jarg2);
//  public final static native void leveldb_options_set_filter_policy(long jarg1, long jarg2);
//  public final static native void leveldb_options_set_error_if_exists(long jarg1, short jarg2);
//  public final static native void leveldb_options_set_paranoid_checks(long jarg1, short jarg2);
//  public final static native void leveldb_options_set_env(long jarg1, long jarg2);
//  public final static native void leveldb_options_set_info_log(long jarg1, long jarg2);
public final static native void leveldb_options_set_write_buffer_size(long optionsPointer, long byteCount);
//  public final static native void leveldb_options_set_max_open_files(long jarg1, int jarg2);
public final static native void leveldb_options_set_cache(long optionsPointer, long cachePointer);
//  public final static native void leveldb_options_set_block_size(long jarg1, long jarg2);
//  public final static native void leveldb_options_set_block_restart_interval(long jarg1, int jarg2);
//  public final static native void leveldb_options_set_max_file_size(long jarg1, long jarg2);
//  public final static native void leveldb_options_set_compression(long jarg1, int jarg2);

  public final static native long leveldb_writeoptions_create();
  public final static native void leveldb_writeoptions_destroy(long writeOptionsPointer);
  public final static native void leveldb_writeoptions_set_sync(long writeOptionsPointer, boolean value);

  public final static native long leveldb_open(long optionsPointer, String dbName);
  public final static native void leveldb_close(long dbPointer);

  public final static native void leveldb_put(long dbPointer, long writeOptionsPointer, ByteBuffer key, long keyByteCount, ByteBuffer value, long valueByteCount);

  public final static native long leveldb_readoptions_create();
  public final static native void leveldb_readoptions_set_verify_checksums(long readOptionsPointer, boolean value);
  public final static native void leveldb_readoptions_set_fill_cache(long readOptionsPointer, boolean value);
  public final static native void leveldb_readoptions_set_snapshot(long readOptionsPointer, long snapshotPointer);
  public final static native void leveldb_readoptions_destroy(long readOptionsPointer);

  public final static native long leveldb_create_snapshot(long dbPointer);
  public final static native void leveldb_release_snapshot(long dbPointer, long snapshotPointer);

  public final static native byte[] leveldb_get(long dbPointer, long readOptionsPointer, ByteBuffer key, long keyByteCount);
  public final static native void leveldb_delete(long dbPointer, long writeOptionsPointer, ByteBuffer key, long keyByteCount);

public final static native long leveldb_cache_create_lru(long byteCount);
public final static native void leveldb_cache_destroy(long cachePointer);

//  public final static native void leveldb_write(long jarg1, long jarg2, long jarg3, long jarg4);
//  public final static native long leveldb_create_iterator(long jarg1, long jarg2);
//  public final static native String leveldb_property_value(long jarg1, String jarg2);
//  public final static native void leveldb_approximate_sizes(long jarg1, int jarg2, long jarg3, long jarg4, long jarg5, long jarg6, long jarg7);
//  public final static native void leveldb_compact_range(long jarg1, String jarg2, long jarg3, String jarg4, long jarg5);
//  public final static native void leveldb_destroy_db(long jarg1, String jarg2, long jarg3);
//  public final static native void leveldb_repair_db(long jarg1, String jarg2, long jarg3);
//  public final static native void leveldb_iter_destroy(long jarg1);
//  public final static native short leveldb_iter_valid(long jarg1);
//  public final static native void leveldb_iter_seek_to_first(long jarg1);
//  public final static native void leveldb_iter_seek_to_last(long jarg1);
//  public final static native void leveldb_iter_seek(long jarg1, String jarg2, long jarg3);
//  public final static native void leveldb_iter_next(long jarg1);
//  public final static native void leveldb_iter_prev(long jarg1);
//  public final static native String leveldb_iter_key(long jarg1, long jarg2);
//  public final static native String leveldb_iter_value(long jarg1, long jarg2);
//  public final static native void leveldb_iter_get_error(long jarg1, long jarg2);
//  public final static native long leveldb_writebatch_create();
//  public final static native void leveldb_writebatch_destroy(long jarg1);
//  public final static native void leveldb_writebatch_clear(long jarg1);
//  public final static native void leveldb_writebatch_put(long jarg1, String jarg2, long jarg3, String jarg4, long jarg5);
//  public final static native void leveldb_writebatch_delete(long jarg1, String jarg2, long jarg3);
//  public final static native void leveldb_writebatch_iterate(long jarg1, long jarg2, long jarg3, long jarg4);
//  public final static native void leveldb_writebatch_append(long jarg1, long jarg2);
//  public final static native int leveldb_no_compression_get();
//  public final static native int leveldb_snappy_compression_get();
//  public final static native long leveldb_comparator_create(long jarg1, long jarg2, long jarg3, long jarg4);
//  public final static native void leveldb_comparator_destroy(long jarg1);
//  public final static native long leveldb_filterpolicy_create(long jarg1, long jarg2, long jarg3, long jarg4, long jarg5);
//  public final static native void leveldb_filterpolicy_destroy(long jarg1);
//  public final static native long leveldb_filterpolicy_create_bloom(int jarg1);

//  public final static native long leveldb_create_default_env();
//  public final static native void leveldb_env_destroy(long jarg1);
//  public final static native String leveldb_env_get_test_directory(long jarg1);
//  public final static native void leveldb_free(long jarg1);
//  public final static native int leveldb_major_version();
//  public final static native int leveldb_minor_version();
}
