g++ -std=c++11 -c com_google_leveldb_NativeLevelDb.c -I$(pwd)/../java/include -Iinclude
g++ -shared com_google_leveldb_NativeLevelDb.o libleveldb.so.1.23.0 -o leveldb-jni.so
cp libleveldb.so.1.23.0 ../../src/main/resources/lib/.
cp leveldb-jni.so ../../src/main/resources/lib/.
