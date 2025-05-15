# gcc -c com_google_leveldb_NativeLevelDb.c -I$(pwd)/../java/include -Iinclude
g++ -std=c++11 -c com_google_leveldb_NativeLevelDb.c -I$(pwd)/../java/include -Iinclude
g++ -shared com_google_leveldb_NativeLevelDb.o libleveldb.1.23.0.dylib -o leveldb-jni.dylib
cp leveldb-jni.dylib ../../src/main/resources/lib/.
